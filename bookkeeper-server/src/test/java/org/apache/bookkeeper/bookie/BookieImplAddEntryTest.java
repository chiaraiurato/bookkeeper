package org.apache.bookkeeper.bookie;

import conf.TestBKConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.client.api.BKException;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.net.BookieId;
import org.apache.bookkeeper.proto.BookkeeperInternalCallbacks.WriteCallback;
import org.awaitility.Awaitility;
import org.junit.*;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.bookkeeper.bookie.BookieImplAddEntryTest.LedgerState.*;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;


@RunWith(Parameterized.class)
public class BookieImplAddEntryTest {

    private final ByteBuf entry;
    private final boolean ackBeforeSync;
    private final WriteCallback callback;
    private final Object ctx;
    private static long rc;
    private final byte[] masterKey;

    private final LedgerState ledgerState ;
    private final boolean expectedException;

    enum LedgerState {
        DEFAULT,
        FENCED,
        NO_DIR_EXC,
    }

    public BookieImplAddEntryTest(EntryTuple entryTuple) throws Exception {
        setUpContext();
        this.entry = entryTuple.entry();
        this.ackBeforeSync = entryTuple.ackBeforeSync();
        this.callback = entryTuple.callback();
        this.ctx = entryTuple.ctx();
        this.masterKey = entryTuple.masterKey();
        this.ledgerState = entryTuple.getLedgerState();
        this.expectedException = entryTuple.expectedException();

    }
    private BookieImpl bookie;

    private static final Logger LOG = LoggerFactory.getLogger(BookieImplAddEntryTest.class);


    public void setUpContext() throws Exception {
        //To run tests we first need to set up the bookie and its configuration
        ServerConfiguration conf = TestBKConfiguration.newServerConfiguration();
        conf.setJournalWriteData(false);
        bookie = spy(new BookieSetUp(conf));
        bookie.start();
    }

    // Define the InvalidCallback class
    private static class InvalidCallback implements WriteCallback {
        @Override
        public void writeComplete(int rc, long ledgerId, long entryId, BookieId addr, Object ctx) {
            throw new RuntimeException("Invalid callback");
        }
    }
    private static WriteCallback mockValidCallback() {
        WriteCallback callback = mock(WriteCallback.class);
        Mockito.doAnswer(invocation -> {
            // Access the arguments passed to the method
            rc = invocation.getArgument(0);
            long ledgerId = invocation.getArgument(1);
            long entryId = invocation.getArgument(2);
            BookieId addr = invocation.getArgument(3);
            Object ctx = invocation.getArgument(4);
            // Log the return code
            LOG.warn("returnCode: {}", rc);
            return null;
        }).when(callback).writeComplete(any(int.class), any(long.class), any(long.class), any(BookieId.class), any());

        return callback;
    }

    private static WriteCallback getInvalidCallback() {
        return new InvalidCallback();
    }

    public static WriteCallback getValidCallback() {
        return mockValidCallback();
    }

    private static ByteBuf getValidByteBuf(){
        //define params
        long ledgerId = 1L;
        long entryId = 1L;
        byte[] data = "This is a test entry".getBytes();
        //create entry
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(ledgerId);
        buf.writeLong(entryId);
        buf.writeBytes(data);
        return buf;
    }
    private static ByteBuf getEmptyByteBuf(){
        return Unpooled.buffer(0);
    }

    private static ByteBuf getInvalidByteBuf(){
        ByteBuf invalidByteBuf = mock(ByteBuf.class);
        doThrow(new RuntimeException("invalid ByteBuf")).when(invalidByteBuf).getByte(Mockito.anyInt());
        return invalidByteBuf;
    }

    // All parameters for addEntry method
    @Parameterized.Parameters
    public static Collection<EntryTuple> provideAddEntryParameters() {
        List<EntryTuple> entryTupleList = new ArrayList<>();

        //TC1 --> SUCCESS
        entryTupleList.add(new EntryTuple(getValidByteBuf(), true, getValidCallback(), "string", "".getBytes(),DEFAULT, false));

        //TC2 --> Failure (NullPointerException)
        entryTupleList.add(new EntryTuple(null, true, getValidCallback(), "string", "masterkey".getBytes(), DEFAULT,true));

        //TC3 -->Failure (ByteBufException)
        entryTupleList.add(new EntryTuple(getInvalidByteBuf(), true, getValidCallback(), "string", "masterkey".getBytes(), DEFAULT,true));

        //TC4 --> Failure (NullPointerException for cb)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), true, null, "string", "masterkey".getBytes(), DEFAULT,true));

        //TC5 --> Failure (Empty entry)
        entryTupleList.add(new EntryTuple(getEmptyByteBuf(), false, getValidCallback(), "string", "masterkey".getBytes(), DEFAULT,true));

        //TC6 --> Failure (ExceptionThrowingCallback)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), true, getInvalidCallback(), "string", "masterkey".getBytes(), DEFAULT,true));

        //TC7 --> Failure (NullPointerException)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), true, getValidCallback(), "string", null, DEFAULT,true));

        //TC8 --> Success (entry added correctly)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), false, getValidCallback(), "string", "".getBytes(),DEFAULT, true));

        //TC9 --> Failure BookieException (JACOCO 1)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), false, getValidCallback(), "string", "".getBytes(), FENCED,true));

        //TC10 --> Failure NoLedgerDirsException (JACOCO 2)
        entryTupleList.add(new EntryTuple(getValidByteBuf(), false, getValidCallback(), "string", "".getBytes(), NO_DIR_EXC, true));
        return entryTupleList;
    }

    @Test
    public void addEntryTest(){

        try {
            switch (ledgerState) {
                case DEFAULT:
                    //To avoid refCnt = 0, the entry must not be referenced
                    bookie.addEntry(entry, ackBeforeSync, callback, ctx, masterKey);
                    // Assert the callback is called with success code
                    Assert.assertEquals(BKException.Code.OK, rc);
                    break;
                case FENCED:
                    CompletableFuture<Boolean> handle =
                     bookie.fenceLedger(3L, masterKey);
                    if(handle!= null){
                        ByteBuf created_entry = bookie.createMasterKeyEntry(3L, masterKey);
                        Assertions.assertThrows(BookieException.LedgerFencedException.class,
                                () -> bookie.addEntry(created_entry, false, callback, ctx, masterKey));

                        //but now we can add entry in recovery mode
                        Assertions.assertDoesNotThrow(() -> bookie.recoveryAddEntry(entry,callback, ctx,masterKey));
                        break;
                    }else{
                        fail("Handle is null");
                    }
                case NO_DIR_EXC:
                    // Configure the bookieImpl mock to throw NoWritableLedgerDirException when getLedgerForEntry is called
                    doThrow(LedgerDirsManager.NoWritableLedgerDirException.class)
                            .when(bookie)
                            .getLedgerForEntry(any(ByteBuf.class), any(byte[].class));

                    // Call addEntry to verify that IOException is thrown
                    //Rejected and not IO cause readEntryTest changed exception
                    Assertions.assertThrows(RejectedExecutionException.class, () -> bookie.addEntry(entry, ackBeforeSync, callback, ctx, masterKey));
                    break;
            }

        } catch (RuntimeException | IOException | BookieException | InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was expected", expectedException);
        }
    }


    // Define the EntryTuple class
    private static final class EntryTuple {
        private final ByteBuf entry;
        private final boolean ackBeforeSync;
        private final WriteCallback callback;
        private final Object ctx;
        private final byte[] masterKey;

        private final LedgerState ledgerState;
        private final boolean expectedException;

        private EntryTuple(ByteBuf entry,
                               boolean ackBeforeSync,
                               WriteCallback callback,
                               Object ctx,
                               byte[] masterKey,
                               LedgerState ledgerState,
                               boolean expectedException) {
            this.entry = entry;
            this.ackBeforeSync = ackBeforeSync;
            this.callback = callback;
            this.ctx = ctx;
            this.masterKey = masterKey;
            this.ledgerState = ledgerState;
            this.expectedException = expectedException;
        }

        public ByteBuf entry() {
            return entry;
        }

        public boolean ackBeforeSync() {
            return ackBeforeSync;
        }

        public WriteCallback callback() {
            return callback;
        }

        public Object ctx() {
            return ctx;
        }

        public byte[] masterKey() {
            return masterKey;
        }

        public LedgerState getLedgerState(){return  ledgerState;}
        public boolean expectedException() {
            return expectedException;
        }
    }
    @After
    public  void cleanUp() {
        bookie.shutdown();

    }

}

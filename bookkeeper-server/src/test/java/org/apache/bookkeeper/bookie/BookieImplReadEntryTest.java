package org.apache.bookkeeper.bookie;

import conf.TestBKConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.client.api.BKException;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.net.BookieId;
import org.apache.bookkeeper.proto.BookkeeperInternalCallbacks.WriteCallback;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class BookieImplReadEntryTest {

    private BookieImpl bookie;
    private long ledgerId;
    private long entryId;
    private boolean expectedException;
    private final Mode mode ;
    private long rc;
    enum Mode {
        READ_ENTRY_DEFAULT,
        READ_ENTRY_NOT_CREATED,
    }
    public BookieImplReadEntryTest(EntryTuple entryTuple) throws Exception {
        setUpContext();
        this.ledgerId = entryTuple.getLedgerId();
        this.entryId = entryTuple.getEntryId();
        this.expectedException = entryTuple.ExpectedException();
        this.mode = entryTuple.Mode();
    }

    public void setUpContext() throws Exception {
        //To run tests we first need to set up the bookie and its configuration
        ServerConfiguration conf = TestBKConfiguration.newServerConfiguration();
        conf.setJournalWriteData(false);
        bookie = new BookieSetUp(conf);
        bookie.start();
    }

    @Parameterized.Parameters
    public static Collection<EntryTuple> provideReadEntryParameters() {
        List<EntryTuple> entryTupleList = new ArrayList<>();

        // TC1 --> Failure (Exception: invalidLedger)
        entryTupleList.add(new EntryTuple(-1L, -1L, Mode.READ_ENTRY_DEFAULT, true));

        // TC2 --> Failure (Exception: invalidLedger)
        entryTupleList.add(new EntryTuple(-1L, 0L,Mode.READ_ENTRY_DEFAULT,   true));

        // TC3 --> Failure (Exception: invalidLedger)
        entryTupleList.add(new EntryTuple(-1L, 1L,Mode.READ_ENTRY_DEFAULT, true));

        // TC4 --> Failure (Exception: invalidEntry)
        //entryTupleList.add(new EntryTuple(0L, -1L, Mode.READ_ENTRY_DEFAULT, true));

        // TC5 --> Success
        entryTupleList.add(new EntryTuple(0L, 0L, Mode.READ_ENTRY_DEFAULT, false));

        // TC6 --> Success
        entryTupleList.add(new EntryTuple(0L, 1L, Mode.READ_ENTRY_DEFAULT, false));

        // TC7 --> Failure (Exception: invalidEntry)
        //entryTupleList.add(new EntryTuple(1L, -1L, Mode.READ_ENTRY_DEFAULT,true));

        // TC8 --> Success
        entryTupleList.add(new EntryTuple(1L, 0L, Mode.READ_ENTRY_DEFAULT, false));

        // TC9 --> Success
        entryTupleList.add(new EntryTuple(1L, 1L,Mode.READ_ENTRY_DEFAULT, false));

        //TC10 --> Failure (PIT added)
        entryTupleList.add(new EntryTuple(100L, 1L,Mode.READ_ENTRY_NOT_CREATED, true));

        return entryTupleList;
    }
    private WriteCallback mockValidCallback() {
        WriteCallback callback = mock(WriteCallback.class);
        Mockito.doAnswer(invocation -> {
            // Access the arguments passed to the method
            rc = invocation.getArgument(0);
            return null;
        }).when(callback).writeComplete(any(int.class), any(long.class), any(long.class), any(BookieId.class), any());

        return callback;
    }
    private ByteBuf createEntry(long ledgerId, long entryId){
        //define params
        byte[] data = "This is a test entry".getBytes();
        //create entry
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(ledgerId);
        buf.writeLong(entryId);
        buf.writeBytes(data);
        return buf;

    }

    @Test
    public void readEntryTest() {
        ByteBuf entry = createEntry(ledgerId, entryId);
        try{
            switch (mode) {
                case READ_ENTRY_DEFAULT:

                        entry.retain();

                        bookie.addEntry(entry, false, mockValidCallback(), null, "".getBytes());
                        Assert.assertEquals(rc, BKException.Code.OK);

                        Assert.assertTrue(bookie.readLastAddConfirmed(ledgerId) > 0);

                        ByteBuf result = bookie.readEntry(ledgerId, entryId);

                        Assert.assertEquals(entry.readLong(), result.readLong());
                        Assert.assertEquals(entry.readLong(), result.readLong());
                        Assert.assertEquals(entry.readableBytes(), result.readableBytes());
                        entry.release();

                        break;
                case READ_ENTRY_NOT_CREATED:
                    try{
                     bookie.readEntry(ledgerId, entryId);
                    }catch (Bookie.NoLedgerException e ){
                        Assert.assertTrue("ledger does not exists", expectedException);
                    }
                    break;
            }
        } catch (BookieException | IOException| IndexOutOfBoundsException | InterruptedException e) {
            e.printStackTrace();
            entry.release();
            Assert.assertTrue("Exception was expected", expectedException);
        }

    }

    // Define the EntryTuple class
    private static final class EntryTuple {

        private long ledgerId;
        private long entryId;
        private final boolean expectedException;
        private final Mode mode;

        private EntryTuple(long ledgerId,
                           long entryId,
                           Mode mode,
                           boolean expectedException) {
            this.ledgerId = ledgerId;
            this.entryId = entryId;
            this.expectedException = expectedException;
            this.mode = mode;
        }

        public long getLedgerId() {
            return ledgerId;
        }

        public long getEntryId() {
            return entryId;
        }
        public Mode Mode(){return  mode;}
        public boolean ExpectedException() {
            return expectedException;
        }
    }
    @After
    public void cleanUp() {
        bookie.shutdown();
    }
}

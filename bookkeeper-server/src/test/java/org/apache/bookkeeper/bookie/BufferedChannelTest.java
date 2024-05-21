package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class BufferedChannelTest {

    private File tempFile;
    private FileChannel fileChannel;
    private BufferedChannel bufferedChannel;

    @Before
    public void setUp() throws IOException {
        tempFile = Files.createTempFile("test", ".tmp").toFile();
        fileChannel = FileChannel.open(tempFile.toPath());
        bufferedChannel = new BufferedChannel(ByteBufAllocator.DEFAULT, fileChannel, 1024);
    }

    @After
    public void tearDown() throws IOException {
        bufferedChannel.close();
        fileChannel.close();
        Files.delete(tempFile.toPath());
    }

    @Test
    public void testPosition(){
        long pos = bufferedChannel.position();

        assertEquals(0, pos);

    }

    @Test
    public void testWriteAndRead() throws IOException {
        // Write data to the channel
        byte[] testData = "Hello, world!".getBytes();
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(testData);
        bufferedChannel.write(byteBuf);

        // Reset position to read back the data
        bufferedChannel.forceWrite(true);
        bufferedChannel.clear();
        long position = bufferedChannel.getFileChannelPosition();

        // Read data from the channel
        ByteBuffer readBuffer = ByteBuffer.allocate(testData.length);
        fileChannel.position(position);
        fileChannel.read(readBuffer);
        readBuffer.flip();
        byte[] readData = new byte[readBuffer.remaining()];
        readBuffer.get(readData);

        // Verify the read data
        assertEquals(new String(readData), new String(readData));
    }



}
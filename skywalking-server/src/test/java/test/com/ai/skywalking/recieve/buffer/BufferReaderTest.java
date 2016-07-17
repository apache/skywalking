package test.com.ai.skywalking.recieve.buffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;

public class BufferReaderTest {
    private File        bufferFile;
    private InputStream bufferInputStream;

    @Before
    public void initData() throws IOException {
        bufferFile = new File("/tmp", "test.aaaa");
        if (!bufferFile.exists()) {
            bufferFile.createNewFile();
        }

        OutputStream outputStream = new FileOutputStream(bufferFile);
        outputStream.write("Hello".getBytes());
        outputStream.close();
        bufferInputStream = new FileInputStream(bufferFile);
    }

    @Test
    public void testReadByte() throws IOException {
        readByte(2000);
    }

    private byte[] readByte(int length) throws IOException {
        byte[] dataContext = new byte[length];
        int realReadLength = bufferInputStream.read(dataContext);
        int remainderLength = length - realReadLength;
        while (remainderLength > 0) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            byte[] remainderByte = new byte[remainderLength];
            int tmpRemainder = bufferInputStream.read(remainderByte, 0, remainderLength);
            if (tmpRemainder == -1) {
                continue;
            }

            System.arraycopy(remainderByte, 0, dataContext, length - remainderLength, tmpRemainder);
            remainderLength -= tmpRemainder;
        }
        return dataContext;
    }

    @After
    public void clearData() throws IOException {
        if (bufferInputStream != null) {
            bufferInputStream.close();
        }
        bufferFile.deleteOnExit();
    }
}

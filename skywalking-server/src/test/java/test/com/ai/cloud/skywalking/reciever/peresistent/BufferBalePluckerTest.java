package test.com.ai.cloud.skywalking.reciever.peresistent;

import com.ai.cloud.skywalking.protocol.util.IntegerAssist;
import com.ai.cloud.skywalking.reciever.peresistent.BufferBalePlucker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class BufferBalePluckerTest {

    private File file;

    @Before
    public void initData() throws IOException {
        file = new File("/tmp", "test.file");
        if (!file.exists()) {
            file.createNewFile();
        }

        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] bytes = "HelloWorld".getBytes();
        byte[] packageByte = Arrays.copyOf(IntegerAssist.intToBytes(bytes.length), bytes.length + 4 + 4);
        System.arraycopy(bytes, 0, packageByte, 4, bytes.length);
        System.arraycopy(new byte[] {127, 127, 127, 127}, 0, packageByte, bytes.length + 4, 4);

        fileOutputStream.write(packageByte);
        fileOutputStream.write(generatePackage("EOF".getBytes()));
        fileOutputStream.close();
    }

    private static byte[] generatePackage(byte[] msg) {
        byte[] dataPackage = new byte[msg.length + 8];
        // 前四位长度
        System.arraycopy(IntegerAssist.intToBytes(msg.length), 0, dataPackage, 0, 4);
        // 中间正文
        System.arraycopy(msg, 0, dataPackage, 4, msg.length);
        // 后四位特殊字符
        System.arraycopy(new byte[] {127, 127, 127, 127}, 0, dataPackage, msg.length + 4, 4);

        return dataPackage;
    }

    @Test
    public void pluck() throws Exception {
        BufferBalePlucker plucker = new BufferBalePlucker(file, 0);
        while (plucker.hasNextBufferBale()) {
            byte[] data = plucker.pluck();
            if (data == null)
                continue;
            assertArrayEquals("HelloWorld".getBytes(), data);
        }
    }

    @Test
    public void skipToNextBufferBale() throws Exception {
        BufferBalePlucker plucker = new BufferBalePlucker(file, 3);
        plucker.skipToNextBufferBale();
        assertNull(plucker.pluck());
    }

    @After
    public void clearData() {
        file.deleteOnExit();
    }

}

package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class SenderTest {
    private int countSize;
    private int threadSize;

    public SenderTest(int threadSize, int countSize) {
        this.threadSize = threadSize;
        this.countSize = countSize;
    }

    @Parameterized.Parameters
    public static Collection<Integer[]> getParams() {
        return Arrays.asList(new Integer[][]{
                {1, 10000000},
        });
    }

    @Test
    public void testSender() throws InterruptedException, IllegalAccessException, IOException {
        InputStream inputStream = ContextBuffer.class.getResourceAsStream("/sky-walking.auth");
        Properties properties = new Properties();
        properties.load(inputStream);
        ConfigInitializer.initialize(properties, Config.class);
        CountDownLatch countDownLatch = new CountDownLatch(threadSize * countSize);
        for (int i = 0; i < threadSize; i++) {
            Thread.sleep(1L);
            new SenderThread(countSize, threadSize + "TTTTTTTTTTTTTTt", countDownLatch).start();
        }
        System.out.println(countDownLatch.getCount());
        countDownLatch.await();
        System.out.println("发送成功");
    }
}

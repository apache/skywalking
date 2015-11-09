package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.buffer.config.BufferConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class SenderTest {
    private int countSize;
    private int threadSize;

    public SenderTest(int threadSize, int countSize, int poolSize, int groupSize, int workerSize, int sendSize) {
        this.threadSize = threadSize;
        this.countSize = countSize;
        BufferConfig.MAX_WORKER = workerSize;
        BufferConfig.GROUP_MAX_SIZE = groupSize;
        BufferConfig.POOL_MAX_SIZE = poolSize;
        BufferConfig.SEND_MAX_SIZE = sendSize;
    }

    @Parameterized.Parameters
    public static Collection<Integer[]> getParams() {
        return Arrays.asList(new Integer[][]{
                {10, 100, 1, 1, 1, 1},
        });
    }

    @Test
    public void testSender() throws InterruptedException {
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

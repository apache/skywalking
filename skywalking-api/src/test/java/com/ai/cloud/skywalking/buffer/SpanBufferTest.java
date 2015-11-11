package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.buffer.config.BufferConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SpanBufferTest {
    public static CountDownLatch countDownLatch;
    private int threadSize = 0;
    private int sizeCount = 0;
    private String fileName = "d:\\test-data.txt";

    public SpanBufferTest(int threadSize, int sizeCount) {
        this.threadSize = threadSize;
        this.sizeCount = sizeCount;
    }

    public int getThreadSize() {
        return threadSize;
    }

    public int getSizeCount() {
        return sizeCount;
    }

    @Parameterized.Parameters
    public static Collection<Integer[]> getParams() {
        return Arrays.asList(new Integer[][]{
                {1, 10001},
//                {2000, 100000, 5, 27000, 3},
//                {2000, 100000, 5, 24000, 3},
//                {2000, 100000, 5, 20000, 2},
//                {1, 20, 5, 16000, 2},
//                {2000, 100000, 5, 14000, 2},
//                {2000, 100000, 5, 21000, 3},

        });
    }

    @Test
    public void testSave() throws Exception {
        while (true) {
            System.out.println(threadSize + "  " + sizeCount);
            CountDownLatch countDownLatch = new CountDownLatch(threadSize);
            long start = System.currentTimeMillis();
            long sleepTime = 1000;
            for (int i = 0; i < threadSize; i++) {
                if (i % 100 == 0) {
                    sleepTime = sleepTime / 2;
                    if (sleepTime == 0) {
                        sleepTime = 5;
                    }
                    Thread.sleep(sleepTime);
                }
                new ContextBufferThread(countDownLatch, sizeCount).start();
            }
            countDownLatch.await();

            Thread.sleep(5000L);
        }
    }
}
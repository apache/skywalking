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

    public SpanBufferTest(int threadSize, int sizeCount, int poolSize, int groupSize, int workerSize) {
        this.threadSize = threadSize;
        this.sizeCount = sizeCount;
        BufferGroup.count = new CountDownLatch(threadSize * sizeCount);
        BufferConfig.MAX_WORKER = workerSize;
        BufferConfig.GROUP_MAX_SIZE = groupSize;
        BufferConfig.POOL_MAX_SIZE = poolSize;
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
                {2000, 100000, 5, 30000, 3},
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
        System.out.println(threadSize + "  " + sizeCount);
        CountDownLatch countDownLatch = new CountDownLatch(threadSize);
        long start = System.currentTimeMillis();
        long sleepTime = 1000;
        for (int i = 0; i < threadSize; i++) {
            if (i % 100 == 0) {
                sleepTime = sleepTime / 2;
                if (sleepTime == 0){
                    sleepTime = 5;
                }
                Thread.sleep(sleepTime);
            }
            new ContextBufferThread(countDownLatch, sizeCount).start();
        }
        countDownLatch.await();
        long end = System.currentTimeMillis() - start;
        CountDownLatch countDownLatchA = new CountDownLatch(threadSize);
        start = System.currentTimeMillis();
        sleepTime = 1000;
        for (int i = 0; i < threadSize; i++) {
            if (i % 100 == 0) {
                sleepTime = sleepTime / 2;
                if (sleepTime == 0){
                    sleepTime = 5;
                }
                Thread.sleep(sleepTime);
            }
            new ContextBufferThreadA(countDownLatchA, sizeCount).start();
        }
        countDownLatchA.await();
        long endA = System.currentTimeMillis() - start;
        System.out.print("执行完毕!");
        StringBuilder builder = new StringBuilder();
        builder.append(threadSize + "\t");
        builder.append(sizeCount + "\t");
        builder.append(BufferConfig.MAX_WORKER + "\t");
        builder.append(BufferConfig.GROUP_MAX_SIZE + "\t");
        builder.append(BufferConfig.POOL_MAX_SIZE + "\t");
        builder.append("1 ms/1\t");
        builder.append((sizeCount * threadSize * 1.0 * 1000) / (end) + "\t");
        builder.append(((end - endA) * 1.0 / end) + "\t");
        builder.append(BufferGroup.count.getCount() + "\t" + (BufferGroup.count.getCount() == 0) + "\t\n");
        appendMethodA(fileName, builder.toString());
        System.out.println("结果输出成功");
        assertEquals(1, 1);
    }


    private void appendMethodA(String fileName, String content) {
        try {
            RandomAccessFile randomFile = new RandomAccessFile(fileName, "rw");
            long fileLength = randomFile.length();
            randomFile.seek(fileLength);
            randomFile.writeBytes(content);
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
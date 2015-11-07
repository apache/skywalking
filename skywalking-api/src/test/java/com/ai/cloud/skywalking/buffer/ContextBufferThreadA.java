package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.context.Span;

import java.util.concurrent.CountDownLatch;

/**
 * Created by astraea on 2015/10/13.
 */
public class ContextBufferThreadA extends Thread {
    private CountDownLatch countDownLatch;
    private int maxSize = 500000;

    public ContextBufferThreadA(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }


    public ContextBufferThreadA(CountDownLatch countDownLatch, int maxSize) {
        this.countDownLatch = countDownLatch;
        this.maxSize = maxSize;
    }

    @Override
    public void run() {
        for (int i = 0; i < maxSize; i++) {
            Span span = new Span();
            span.setLevelId(i);
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // ContextBuffer.save(context);
        }
        countDownLatch.countDown();
    }
}

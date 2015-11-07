package com.ai.cloud.skywalking.sender;

import java.util.concurrent.CountDownLatch;

public class SenderThread extends Thread {

    private int countSize;
    private String msg;
    private static CountDownLatch countDownLatch;

    public SenderThread(int countSize, String msg) {
        this.countSize = countSize;
        this.msg = msg;
    }

    public SenderThread(int countSize, String msg, CountDownLatch countDownLatch) {
        this.countSize = countSize;
        this.msg = msg;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        for (int i = 0; i < countSize; i++) {
            DataSenderFactory.getSender().send(msg + countSize + " ");
            countDownLatch.countDown();
        }
    }
}

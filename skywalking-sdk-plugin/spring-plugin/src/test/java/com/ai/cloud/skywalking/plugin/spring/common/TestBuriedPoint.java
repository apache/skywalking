package com.ai.cloud.skywalking.plugin.spring.common;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBuriedPoint extends Thread {

    private static final int MAX_THREAD_SIZE = 1;
    private CallChainA callChainA;
    AtomicInteger atomicInteger = new AtomicInteger();

    public TestBuriedPoint(CallChainA callChainA) {
        this.callChainA = callChainA;
    }

    private Timer timer = new Timer(Thread.currentThread().getName());

    public TestBuriedPoint() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(atomicInteger.get());
            }
        }, 1000);

    }

    @Override
    public void run() {
        for (int i = 0 ; i < 1; i++) {
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                callChainA.doBusiness();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + "\t" + atomicInteger.incrementAndGet());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        CallChainA callChainA = classPathXmlApplicationContext.getBean(CallChainA.class);
        CountDownLatch countDownLatch = new CountDownLatch(10);
        for (int i = 0; i < MAX_THREAD_SIZE; i++) {
            new TestBuriedPoint(callChainA).start();
        }

        countDownLatch.await();

    }
}

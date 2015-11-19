package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainD {

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        //System.out.println("three");
    }

}

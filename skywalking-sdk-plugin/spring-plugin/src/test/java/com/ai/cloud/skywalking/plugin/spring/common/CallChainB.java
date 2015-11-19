package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainB {

    private CallChainC callChainC;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        callChainC.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
    }

    public CallChainC getCallChainC() {
        return callChainC;
    }

    public void setCallChainC(CallChainC callChainC) {
        this.callChainC = callChainC;
    }
}

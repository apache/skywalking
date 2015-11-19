package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainC {

    private CallChainE callChainE;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        callChainE.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
    }

    public CallChainE getCallChainE() {
        return callChainE;
    }

    public void setCallChainE(CallChainE callChainE) {
        this.callChainE = callChainE;
    }
}

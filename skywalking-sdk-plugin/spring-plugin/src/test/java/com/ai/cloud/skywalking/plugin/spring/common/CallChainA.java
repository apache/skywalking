package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainA {
    private CallChainB callChainB;
    private CallChainD callChainD;
    private CallChainF callChainF;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        callChainB.doBusiness();
        callChainD.doBusiness();
        callChainF.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
    }

    public CallChainB getCallChainB() {
        return callChainB;
    }

    public void setCallChainB(CallChainB callChainB) {
        this.callChainB = callChainB;
    }

    public CallChainD getCallChainD() {
        return callChainD;
    }

    public void setCallChainD(CallChainD callChainD) {
        this.callChainD = callChainD;
    }

    public CallChainF getCallChainF() {
        return callChainF;
    }

    public void setCallChainF(CallChainF callChainF) {
        this.callChainF = callChainF;
    }
}

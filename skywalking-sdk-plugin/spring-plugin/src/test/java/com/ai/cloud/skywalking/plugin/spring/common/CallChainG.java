package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainG {

    private CallChainH callChainH;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        BusinessKeyAppender.trace("key-value");
        callChainH.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));

    }

    public CallChainH getCallChainH() {
        return callChainH;
    }

    public void setCallChainH(CallChainH callChainH) {
        this.callChainH = callChainH;
    }
}

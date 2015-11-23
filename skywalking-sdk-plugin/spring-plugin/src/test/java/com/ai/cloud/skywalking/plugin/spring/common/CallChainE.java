package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainE {

    private CallChainG callChainG;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        BusinessKeyAppender.setBusinessKey2Trace("key-value");
        callChainG.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
    }

    public CallChainG getCallChainG() {
        return callChainG;
    }

    public void setCallChainG(CallChainG callChainG) {
        this.callChainG = callChainG;
    }
}

package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainH {

    private CallChainI callChainI;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        BusinessKeyAppender.setBusinessKey2Trace("key-value");
        callChainI.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));

    }

    public CallChainI getCallChainI() {
        return callChainI;
    }

    public void setCallChainI(CallChainI callChainI) {
        this.callChainI = callChainI;
    }
}

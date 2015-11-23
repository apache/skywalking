package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainI {
    private CallChainJ callChainJ;

    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        BusinessKeyAppender.setBusinessKey2Trace("key-value");
        callChainJ.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));

    }

    public CallChainJ getCallChainJ() {
        return callChainJ;
    }

    public void setCallChainJ(CallChainJ callChainJ) {
        this.callChainJ = callChainJ;
    }
}

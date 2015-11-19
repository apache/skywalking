package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.api.BusinessKeyAppender;
import com.ai.cloud.skywalking.plugin.spring.Tracing;

import java.util.concurrent.ThreadLocalRandom;

public class CallChainJ {
    private CallChainK callChainK;
    @Tracing
    public void doBusiness() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        BusinessKeyAppender.trace("key-value");
        callChainK.doBusiness();
        Thread.sleep(ThreadLocalRandom.current().nextInt(10));

    }

    public CallChainK getCallChainK() {
        return callChainK;
    }

    public void setCallChainK(CallChainK callChainK) {
        this.callChainK = callChainK;
    }
}

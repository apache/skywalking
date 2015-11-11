package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class CallChainB {

    private CallChainC callChainC;

    @Tracing
    public void doBusiness() {
        callChainC.doBusiness();
    }

    public CallChainC getCallChainC() {
        return callChainC;
    }

    public void setCallChainC(CallChainC callChainC) {
        this.callChainC = callChainC;
    }
}

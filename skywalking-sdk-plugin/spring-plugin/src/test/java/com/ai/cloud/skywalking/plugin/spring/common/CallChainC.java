package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class CallChainC {

    private CallChainE callChainE;

    @Tracing
    public void doBusiness() {
        callChainE.doBusiness();
    }

    public CallChainE getCallChainE() {
        return callChainE;
    }

    public void setCallChainE(CallChainE callChainE) {
        this.callChainE = callChainE;
    }
}

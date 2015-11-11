package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class CallChainC {

    @Tracing
    public void doBusiness() {
        System.out.println("I'm here");
    }
}

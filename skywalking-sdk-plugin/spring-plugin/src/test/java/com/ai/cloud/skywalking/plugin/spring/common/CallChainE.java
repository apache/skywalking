package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class CallChainE {

    @Tracing
    public void doBusiness() {
       // System.out.println("I'm here");
    }
}

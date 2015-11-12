package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class CallChainF {

    @Tracing
    public void doBusiness() {
        //System.out.println("me too");
    }
}

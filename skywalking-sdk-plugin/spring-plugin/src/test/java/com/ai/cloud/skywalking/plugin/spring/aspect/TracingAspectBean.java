package com.ai.cloud.skywalking.plugin.spring.aspect;

import com.ai.cloud.skywalking.plugin.spring.Tracing;

public class TracingAspectBean {

    @Tracing
    public String doBusiness() {
        System.out.println("do business");
        return "Hello World";
    }
}

package com.ai.cloud.skywalking.plugin.spring.aspect;

public class AspectBean {
    public void before() {
        System.out.println("before aspect");
    }

    public void after() {
        System.out.println("after aspect");
    }
}

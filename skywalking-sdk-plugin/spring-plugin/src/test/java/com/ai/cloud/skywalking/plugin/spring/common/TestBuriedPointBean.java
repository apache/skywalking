package com.ai.cloud.skywalking.plugin.spring.common;

import com.ai.cloud.skywalking.plugin.spring.Tracing;


public class TestBuriedPointBean {
    @Tracing
    public void sayTest() throws RuntimeException, IllegalStateException {
        System.out.println("say Test");
    }

    @Tracing
    public static void sayTest1() {
        System.out.println("xx");
    }

    @Tracing
    public String addStr(int a, String c) {
        throw new RuntimeException("aa");
    }

    @Tracing
    public String addStr(int a) {
        throw new RuntimeException("aa");
    }

}

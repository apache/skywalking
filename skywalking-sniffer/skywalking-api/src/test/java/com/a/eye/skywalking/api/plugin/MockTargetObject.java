package com.a.eye.skywalking.api.plugin;

public class MockTargetObject {

    private String value;

    public MockTargetObject(String value) {
        this.value = value;
    }

    public String targetInstanceMethod() {
        return "targetInstanceMethod";
    }

    public String targetInstanceMethodWithException() {
        throw new RuntimeException("test exception");
    }

    public static String targetStaticMethod() {
        return "targetStaticMethod";
    }
}

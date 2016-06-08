package com.ai.cloud.skywalking.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class MethodNameMatcher {

    private String methodMatchDescribe;

    private int argNum = -1;

    private Class<?>[] argTypeArray;

    public MethodNameMatcher(String methodMatchDescribe) {
        this.methodMatchDescribe = methodMatchDescribe;
    }

    public MethodNameMatcher(String methodMatchDescribe, int argNum) {
        this.methodMatchDescribe = methodMatchDescribe;
        this.argNum = argNum;
    }

    public MethodNameMatcher(String methodMatchDescribe, Class<?>[] argTypeArray) {
        this.argTypeArray = argTypeArray;
        this.methodMatchDescribe = methodMatchDescribe;
    }

    public abstract ElementMatcher<MethodDescription> builderMatcher();

    protected String getMethodMatchDescribe() {
        return methodMatchDescribe;
    }

    protected int getArgNum() {
        return argNum;
    }

    protected Class<?>[] getArgTypeArray() {
        return argTypeArray;
    }
}

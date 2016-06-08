package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class SimpleMethodMatcher extends com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher {

    public SimpleMethodMatcher(String methodName) {
        super(methodName);
    }

    public SimpleMethodMatcher(String methodName, int argNum) {
        super(methodName, argNum);
    }

    public SimpleMethodMatcher(String methodName, Class<?>... args) {
        super(methodName, args);
    }


    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe) {
        super(modifier, methodMatchDescribe);
    }

    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe, int argNum) {
        super(modifier, methodMatchDescribe, argNum);
    }

    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe, Class<?>[] argTypeArray) {
        super(modifier, methodMatchDescribe, argTypeArray);
    }


    @Override
    public ElementMatcher.Junction<MethodDescription> builderMatcher() {
        ElementMatcher.Junction<MethodDescription> matcher = named(getMethodMatchDescribe());
        return mergeArgumentsIfNecessary(matcher);
    }
}

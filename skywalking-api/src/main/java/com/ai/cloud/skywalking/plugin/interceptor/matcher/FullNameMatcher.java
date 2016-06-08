package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;

public class FullNameMatcher extends MethodNameMatcher {

    public FullNameMatcher(String methodName) {
        super(methodName);
    }

    public FullNameMatcher(String methodName, int argNum) {
        super(methodName, argNum);
    }

    public FullNameMatcher(String methodName, Class<?>... args) {
        super(methodName, args);
    }

    @Override
    public ElementMatcher<MethodDescription> builderMatcher() {
        ElementMatcher.Junction<MethodDescription> matcher = named(getMethodMatchDescribe());

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

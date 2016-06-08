package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

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
    public ElementMatcher builderMatcher() {
        ElementMatcher.Junction matcher = named(getMethodMatchDescribe());

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

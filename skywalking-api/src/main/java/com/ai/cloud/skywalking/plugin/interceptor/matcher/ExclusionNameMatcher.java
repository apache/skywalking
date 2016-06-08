package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ExclusionNameMatcher extends MethodNameMatcher {

    public ExclusionNameMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    public ExclusionNameMatcher(String methodMatchDescribe, int argNum) {
        super(methodMatchDescribe, argNum);
    }

    public ExclusionNameMatcher(String methodMatchDescribe, Class[] argTypeArray) {
        super(methodMatchDescribe, argTypeArray);
    }

    @Override
    public ElementMatcher builderMatcher() {
        ElementMatcher.Junction matcher = not(named(getMethodMatchDescribe()));

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

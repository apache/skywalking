package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;

public class ExclusionNameMatcher extends MethodNameMatcher {

    public ExclusionNameMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    public ExclusionNameMatcher(String methodMatchDescribe, int argNum) {
        super(methodMatchDescribe, argNum);
    }

    public ExclusionNameMatcher(String methodMatchDescribe, Class<?>[] argTypeArray) {
        super(methodMatchDescribe, argTypeArray);
    }

    @Override
    public ElementMatcher<MethodDescription> builderMatcher() {
        ElementMatcher.Junction<MethodDescription> matcher = not(named(getMethodMatchDescribe()));

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

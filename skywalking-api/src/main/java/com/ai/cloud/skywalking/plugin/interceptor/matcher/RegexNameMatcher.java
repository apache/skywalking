package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;

public class RegexNameMatcher extends MethodNameMatcher {

    public RegexNameMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    public RegexNameMatcher(String methodMatchDescribe, int argNum) {
        super(methodMatchDescribe, argNum);
    }

    public RegexNameMatcher(String methodMatchDescribe, Class<?>[] argTypeArray) {
        super(methodMatchDescribe, argTypeArray);
    }

    @Override
    public ElementMatcher<MethodDescription> builderMatcher() {
        ElementMatcher.Junction<MethodDescription> matcher = nameMatches(getMethodMatchDescribe());

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

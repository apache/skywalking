package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class RegexNameMatcher extends MethodNameMatcher {

    public RegexNameMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    public RegexNameMatcher(String methodMatchDescribe, int argNum) {
        super(methodMatchDescribe, argNum);
    }

    public RegexNameMatcher(String methodMatchDescribe, Class[] argTypeArray) {
        super(methodMatchDescribe, argTypeArray);
    }

    @Override
    public ElementMatcher builderMatcher() {
        ElementMatcher.Junction matcher = nameMatches(getMethodMatchDescribe());

        if (getArgTypeArray() != null) {
            matcher.and(takesArguments(getArgTypeArray()));
        }

        if (getArgNum() > -1) {
            matcher.and(takesArguments(getArgNum()));
        }

        return matcher;
    }
}

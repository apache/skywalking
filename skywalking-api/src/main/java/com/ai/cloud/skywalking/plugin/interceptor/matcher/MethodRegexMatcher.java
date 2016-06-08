package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;

public class MethodRegexMatcher extends MethodMatcher {

    public MethodRegexMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    public MethodRegexMatcher(String methodMatchDescribe, int argNum) {
        super(methodMatchDescribe, argNum);
    }

    public MethodRegexMatcher(String methodMatchDescribe, Class<?>[] argTypeArray) {
        super(methodMatchDescribe, argTypeArray);
    }

    public MethodRegexMatcher(Modifier modifier, String methodMatchDescribe) {
        super(modifier, methodMatchDescribe);
    }

    public MethodRegexMatcher(Modifier modifier, String methodMatchDescribe, int argNum) {
        super(modifier, methodMatchDescribe, argNum);
    }

    public MethodRegexMatcher(Modifier modifier, String methodMatchDescribe, Class<?>[] argTypeArray) {
        super(modifier, methodMatchDescribe, argTypeArray);
    }


    @Override
    public ElementMatcher.Junction<MethodDescription> builderMatcher() {
        ElementMatcher.Junction<MethodDescription> matcher = nameMatches(getMethodMatchDescribe());
        return mergeArgumentsIfNecessary(matcher);
    }
}

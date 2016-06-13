package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class PrivateMethodMatcher extends MethodMatcher {
    public PrivateMethodMatcher() {
        super("any private method");
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> buildMatcher() {
        return any().and(ElementMatchers.<MethodDescription>isPrivate());
    }

    @Override
    public String toString() {
        return getMethodMatchDescribe();
    }
}

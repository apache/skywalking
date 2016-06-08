package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import static net.bytebuddy.matcher.ElementMatchers.any;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;

public class AnyMethodMatcher extends MethodNameMatcher {

    public AnyMethodMatcher() {
        super("*");
    }

    @Override
    public ElementMatcher<MethodDescription> builderMatcher() {
        return any();
    }
}

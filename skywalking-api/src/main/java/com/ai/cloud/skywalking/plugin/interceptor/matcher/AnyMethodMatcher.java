package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class AnyMethodMatcher extends MethodNameMatcher {

    public AnyMethodMatcher() {
        super("*");
    }

    @Override
    public ElementMatcher builderMatcher() {
        return any();
    }
}

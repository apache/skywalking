package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class AnyMethodMatcher extends MethodMatcher {

    public AnyMethodMatcher() {
        super("*");
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> builderMatcher() {
        return any();
    }
}

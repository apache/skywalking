package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class AnyMethodMatcher extends ExclusiveObjectDefaultMethodMatcher {

    public AnyMethodMatcher() {
        super("any method");
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> match() {
        return any();
    }

    @Override
    public String toString() {
        return getMethodMatchDescribe();
    }
}

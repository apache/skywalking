package com.a.eye.skywalking.plugin.interceptor.matcher;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class AnyMethodsMatcher extends ExclusiveObjectDefaultMethodsMatcher {

    public AnyMethodsMatcher() {
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

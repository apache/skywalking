package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import javassist.CtMethod;

public class PrivateMethodMatcher extends MethodMatcher {
    public PrivateMethodMatcher() {
        super("any private method");
    }

    @Override
    public boolean match(CtMethod ctMethod) {
        return java.lang.reflect.Modifier.isPrivate(ctMethod.getModifiers());
    }

    @Override
    public String toString() {
        return getMethodMatchDescribe();
    }
}

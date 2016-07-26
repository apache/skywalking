package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import javassist.CtMethod;

public abstract class ExclusiveObjectDefaultMethodsMatcher extends MethodMatcher {

    private static final MethodMatcher[] EXCLUSIVE_DEFAULT_METHOD_NAME =
            new MethodMatcher[] {new SimpleMethodMatcher(Modifier.Public, "finalize", 0), new SimpleMethodMatcher(Modifier.Public, "wait", long.class, int.class),
                    new SimpleMethodMatcher(Modifier.Public, "wait", long.class), new SimpleMethodMatcher(Modifier.Public, "wait", 0),
                    new SimpleMethodMatcher(Modifier.Public, "equals", Object.class), new SimpleMethodMatcher(Modifier.Public, "toString", 0),
                    new SimpleMethodMatcher(Modifier.Public, "hashCode", 0), new SimpleMethodMatcher(Modifier.Public, "getClass", 0),
                    new SimpleMethodMatcher(Modifier.Public, "clone", 0), new SimpleMethodMatcher(Modifier.Public, "notify", 0),
                    new SimpleMethodMatcher(Modifier.Public, "notifyAll", 0)};

    public ExclusiveObjectDefaultMethodsMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    @Override
    public boolean match(CtMethod ctMethod) {
        return this.matchMethod(ctMethod) && excludeObjectDefaultMethod(ctMethod);
    }


    protected boolean excludeObjectDefaultMethod(CtMethod ctMethod) {
        for (MethodMatcher methodMatcher : EXCLUSIVE_DEFAULT_METHOD_NAME) {
            if (methodMatcher.match(ctMethod)) {
                return false;
            }
        }
        return true;
    }

    public abstract boolean matchMethod(CtMethod ctMethod);

}

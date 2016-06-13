package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class ExclusiveObjectDefaultMethodMatcher extends MethodMatcher {

    private static final String[] EXCLUSIVE_DEFAULT_METHOD_NAME = new String[]{
            "finalize", "wait", "equals",
            "toString", "hashCode", "getClass",
            "clone", "notify", "notifyAll"
    };

    public ExclusiveObjectDefaultMethodMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> buildMatcher() {
        return this.match().and(excludeObjectDefaultMethod());
    }

    protected ElementMatcher.Junction<MethodDescription> excludeObjectDefaultMethod() {
        ElementMatcher.Junction<MethodDescription> exclusiveMatcher = null;
        for (String methodName : EXCLUSIVE_DEFAULT_METHOD_NAME) {
            if (exclusiveMatcher == null) {
                exclusiveMatcher = named(methodName);
                continue;
            }

            exclusiveMatcher = exclusiveMatcher.or(named(methodName));

        }
        return not(exclusiveMatcher);
    }

    public abstract ElementMatcher.Junction<MethodDescription> match();


}

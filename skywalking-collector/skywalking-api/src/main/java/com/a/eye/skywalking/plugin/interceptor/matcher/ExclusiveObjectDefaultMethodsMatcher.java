package com.a.eye.skywalking.plugin.interceptor.matcher;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class ExclusiveObjectDefaultMethodsMatcher extends MethodMatcher {

    private static final MethodMatcher[] EXCLUSIVE_DEFAULT_METHOD_NAME = new MethodMatcher[]{
            new SimpleMethodMatcher(Modifier.Public, "finalize", 0),
            new SimpleMethodMatcher(Modifier.Public, "wait", long.class, int.class),
            new SimpleMethodMatcher(Modifier.Public, "wait", long.class),
            new SimpleMethodMatcher(Modifier.Public, "wait", 0),
            new SimpleMethodMatcher(Modifier.Public, "equals", Object.class),
            new SimpleMethodMatcher(Modifier.Public, "toString", 0),
            new SimpleMethodMatcher(Modifier.Public, "hashCode", 0),
            new SimpleMethodMatcher(Modifier.Public, "getClass", 0),
            new SimpleMethodMatcher(Modifier.Public, "clone", 0),
            new SimpleMethodMatcher(Modifier.Public, "notify", 0),
            new SimpleMethodMatcher(Modifier.Public, "notifyAll", 0)
    };

    public ExclusiveObjectDefaultMethodsMatcher(String methodMatchDescribe) {
        super(methodMatchDescribe);
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> buildMatcher() {
        return this.match().and(excludeObjectDefaultMethod());
    }

    protected ElementMatcher.Junction<MethodDescription> excludeObjectDefaultMethod() {
        ElementMatcher.Junction<MethodDescription> exclusiveMatcher = null;
        for (MethodMatcher methodMatcher : EXCLUSIVE_DEFAULT_METHOD_NAME) {
            if (exclusiveMatcher == null) {
                exclusiveMatcher = methodMatcher.buildMatcher();
                continue;
            }

            exclusiveMatcher = exclusiveMatcher.or(methodMatcher.buildMatcher());

        }
        return not(exclusiveMatcher);
    }

    public abstract ElementMatcher.Junction<MethodDescription> match();


}

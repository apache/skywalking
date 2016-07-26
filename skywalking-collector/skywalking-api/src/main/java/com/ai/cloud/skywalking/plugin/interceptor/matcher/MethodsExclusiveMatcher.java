package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import javassist.CtMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodsExclusiveMatcher extends ExclusiveObjectDefaultMethodsMatcher {

    private List<MethodMatcher> matchers = new ArrayList<MethodMatcher>();

    public MethodsExclusiveMatcher(String... methodNames) {
        super("exclude method name: " + methodNames.toString());
        for (String methodName : methodNames) {
            matchers.add(new SimpleMethodMatcher(methodName));
        }
    }

    public MethodsExclusiveMatcher(MethodMatcher... matchers) {
        super("exclude methods description :" + matchers.toString());
        this.matchers.addAll(Arrays.asList(matchers));
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("exclude following method(s): ");
        int idx = 1;
        for (MethodMatcher methodMatcher : matchers) {
            stringBuilder.append(idx++ + "." + methodMatcher.toString() + ". ");
        }

        return stringBuilder.toString();
    }

    @Override
    public boolean matchMethod(CtMethod ctMethod) {
        boolean result = false;
        for (MethodMatcher methodMatcher : matchers) {
            if (methodMatcher.match(ctMethod)) {
                result = result || result;
            }
        }
        return !result;
    }
}

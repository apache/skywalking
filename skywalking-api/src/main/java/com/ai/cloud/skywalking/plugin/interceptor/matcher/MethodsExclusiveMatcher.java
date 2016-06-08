package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Created by xin on 16-6-8.
 */
public class MethodsExclusiveMatcher extends MethodMatcher {

    private List<MethodMatcher> matchers = new ArrayList<MethodMatcher>();

    public MethodsExclusiveMatcher(String... methodNames) {
        super("Exclusive method name: " + methodNames.toString());
        for (String methodName : methodNames) {
            matchers.add(new SimpleMethodMatcher(methodName));
        }
    }

    public MethodsExclusiveMatcher(MethodMatcher... matchers) {
        super("Exclusive methods description :" + matchers.toString());
        this.matchers.addAll(Arrays.asList(matchers));
    }

    @Override
    public ElementMatcher.Junction<MethodDescription> builderMatcher() {

        ElementMatcher.Junction<MethodDescription> result = null;

        for (MethodMatcher matcher : matchers) {
            if (result == null) {
                result = matcher.builderMatcher();
                continue;
            }

            result = result.or(matcher.builderMatcher());
        }

        return not(result);
    }

}

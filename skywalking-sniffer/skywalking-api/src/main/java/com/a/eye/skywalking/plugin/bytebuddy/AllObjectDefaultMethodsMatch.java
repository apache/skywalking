package com.a.eye.skywalking.plugin.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * match all methods, which inherits from {@link Object}
 * <p>
 * Created by wusheng on 2017/1/3.
 */
public enum AllObjectDefaultMethodsMatch implements ElementMatcher<MethodDescription> {
    /**
     * Stay in singleton.
     */
    INSTANCE;

    /**
     * The matcher will be init in constructor and stay permanent.
     */
    private final ElementMatcher.Junction<MethodDescription> matcher;

    /**
     * Init the match, include {@link Object}'s methods.
     */
    AllObjectDefaultMethodsMatch() {
        ElementMatcher.Junction<MethodDescription>[] allDefaultMethods = new ElementMatcher.Junction[] {named("finalize").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("wait").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("wait").and(takesArguments(long.class, int.class)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("wait").and(takesArguments(long.class)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("equals").and(takesArguments(Object.class)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("toString").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("hashCode").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("getClass").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("clone").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("notify").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic()),
                named("notifyAll").and(takesArguments(0)).and(ElementMatchers.<MethodDescription>isPublic())};

        ElementMatcher.Junction<MethodDescription> newMatcher = null;
        for (int i = 0; i < allDefaultMethods.length; i++) {
            if(i == 0){
                newMatcher = allDefaultMethods[i];
            }else{
                newMatcher = newMatcher.or(allDefaultMethods[i]);
            }
        }

        matcher = newMatcher;
    }

    /**
     * @param target method description.
     * @return true, if the method inherit from {@link Object}'s methods.
     */
    @Override
    public boolean matches(MethodDescription target) {
        return matcher.matches(target);
    }
}

package com.ai.cloud.skywalking.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class MethodMatcher {

    private String methodMatchDescribe;

    private int argNum = -1;

    private Class<?>[] argTypeArray;

    private Modifier modifier;

    public MethodMatcher(String methodMatchDescribe) {
        this(null, methodMatchDescribe);
    }

    public MethodMatcher(Modifier modifier, String methodMatchDescribe) {
        this.methodMatchDescribe = methodMatchDescribe;
        this.modifier = modifier;
    }


    public MethodMatcher(String methodMatchDescribe, int argNum) {
        this(null, methodMatchDescribe, argNum);
    }

    public MethodMatcher(Modifier modifier, String methodMatchDescribe, int argNum) {
        this.methodMatchDescribe = methodMatchDescribe;
        this.argNum = argNum;
        this.modifier = modifier;
    }

    public MethodMatcher(String methodMatchDescribe, Class<?>[] argTypeArray) {
        this(null, methodMatchDescribe, argTypeArray);
    }

    public MethodMatcher(Modifier modifier, String methodMatchDescribe, Class<?>[] argTypeArray) {
        this.argTypeArray = argTypeArray;
        this.methodMatchDescribe = methodMatchDescribe;
        this.modifier = modifier;
    }

    public abstract ElementMatcher.Junction<MethodDescription> builderMatcher();

    protected String getMethodMatchDescribe() {
        return methodMatchDescribe;
    }

    protected ElementMatcher.Junction<MethodDescription> mergeArgumentsIfNecessary(ElementMatcher.Junction<MethodDescription> matcher) {
        if (argTypeArray != null) {
            matcher = matcher.and(takesArguments(argTypeArray));
        }

        if (argNum > -1) {
            matcher = matcher.and(takesArguments(argNum));
        }

        if (modifier != null) {
            matcher = matcher.and(modifier.elementMatcher());
        }

        return matcher;
    }

    public enum Modifier {
        Public, Default, Private, Protected;

        private ElementMatcher elementMatcher() {
            switch (this) {
                case Private: {
                    return isPrivate();
                }
                case Default: {
                    return isPackagePrivate();
                }
                case Public: {
                    return isPublic();
                }
                case Protected: {
                    return isProtected();
                }
                default:
                    return isPublic();
            }
        }
    }

}

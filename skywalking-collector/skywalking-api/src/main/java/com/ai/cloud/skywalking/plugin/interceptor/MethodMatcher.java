package com.ai.cloud.skywalking.plugin.interceptor;

import javassist.CtMethod;

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

    protected String getMethodMatchDescribe() {
        return methodMatchDescribe;
    }


    public abstract boolean match(CtMethod ctMethod);

    public enum Modifier {
        Public(0x00000001),
        Default(0x00000000),
        Private(0x00000002),
        Protected(0x00000004);

        private int value;


        Modifier(int value) {
            this.value = value;
        }


        public int getValue() {
            return value;
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("method name=" + getMethodMatchDescribe());
        if (getModifier() != null) {
            stringBuilder.insert(0, getModifier() + " ");
        }

        if (getArgNum() > -1) {
            stringBuilder.append(", argnum=" + getArgNum());
        }

        if (getArgTypeArray() != null) {
            stringBuilder.append(",  types of arguments are ");
            boolean isFirst = true;
            for (Class<?> argType : getArgTypeArray()) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    stringBuilder.append(",");
                }
                stringBuilder.append(argType.getName());
            }
        }
        return stringBuilder.toString();
    }

    protected int getArgNum() {
        return argNum;
    }

    protected Class<?>[] getArgTypeArray() {
        return argTypeArray;
    }

    protected Modifier getModifier() {
        return modifier;
    }
}

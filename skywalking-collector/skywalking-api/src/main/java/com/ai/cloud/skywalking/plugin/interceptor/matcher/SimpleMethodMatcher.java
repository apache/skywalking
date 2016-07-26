package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import javassist.CtMethod;
import javassist.NotFoundException;

public class SimpleMethodMatcher extends MethodMatcher {

    public SimpleMethodMatcher(String methodName) {
        super(methodName);
    }

    public SimpleMethodMatcher(String methodName, int argNum) {
        super(methodName, argNum);
    }

    public SimpleMethodMatcher(String methodName, Class<?>... args) {
        super(methodName, args);
    }


    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe) {
        super(modifier, methodMatchDescribe);
    }

    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe, int argNum) {
        super(modifier, methodMatchDescribe, argNum);
    }

    public SimpleMethodMatcher(Modifier modifier, String methodMatchDescribe, Class<?>... argTypeArray) {
        super(modifier, methodMatchDescribe, argTypeArray);
    }

    @Override
    public boolean match(CtMethod ctMethod) {
        int result = 1;
        try {
            result <<= matchArgTypeArrayIfNecessary(ctMethod);
            result <<= matchArgNumIfNecessary(ctMethod);
            result <<= matchModifierIfNecessary(ctMethod);
            return result == 1 ? true : false;
        } catch (Exception e) {
            return false;
        }
    }

    private int matchModifierIfNecessary(CtMethod ctMethod) {
        if (getModifier() != null) {
            return getModifier().getValue() == ctMethod.getModifiers() ? 0 : 1;
        }
        return 0;
    }

    private int matchArgNumIfNecessary(CtMethod ctMethod) throws NotFoundException {
        if (getArgNum() > -1) {
            return getArgNum() == ctMethod.getParameterTypes().length ? 0 : 1;
        }
        return 0;
    }

    private int matchArgTypeArrayIfNecessary(CtMethod ctMethod) throws NotFoundException {
        if (getArgTypeArray() != null) {
            for (int i = 0; i < getArgTypeArray().length; i++) {
                if (!getArgTypeArray()[i].getName().equals(ctMethod.getParameterTypes()[i].getName())) {
                    return 1;
                }
            }
        }
        return 0;
    }

}

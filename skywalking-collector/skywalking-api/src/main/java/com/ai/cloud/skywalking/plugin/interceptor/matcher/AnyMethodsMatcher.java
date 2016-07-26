package com.ai.cloud.skywalking.plugin.interceptor.matcher;

import javassist.CtMethod;

public class AnyMethodsMatcher extends ExclusiveObjectDefaultMethodsMatcher {

    public AnyMethodsMatcher() {
        super("any method");
    }


    @Override
    public String toString() {
        return getMethodMatchDescribe();
    }

    @Override
    public boolean matchMethod(CtMethod ctMethod) {
        return true;
    }
}

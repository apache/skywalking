package com.a.eye.skywalking.plugin.motan.define;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class MotanServerDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected String enhanceClassName() {
        return "com.weibo.api.motan.rpc.AbstractProvider";
    }

    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[] {new SimpleMethodMatcher("call")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.motan.MotanServerInterceptor";
    }
}

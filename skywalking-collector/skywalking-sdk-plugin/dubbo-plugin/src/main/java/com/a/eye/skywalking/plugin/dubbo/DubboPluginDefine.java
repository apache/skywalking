package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public class DubboPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("invoke")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.dubbo.MonitorFilterInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        return "com.alibaba.dubbo.monitor.support.MonitorFilter";
    }
}

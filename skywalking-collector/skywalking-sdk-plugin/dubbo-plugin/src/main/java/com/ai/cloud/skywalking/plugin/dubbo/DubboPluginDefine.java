package com.ai.cloud.skywalking.plugin.dubbo;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class DubboPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("invoke")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.dubbo.MonitorFilterInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        return "com.alibaba.dubbo.monitor.support.MonitorFilter";
    }
}

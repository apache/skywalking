package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public class DubboPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.alibaba.dubbo.monitor.support.MonitorFilter";
    }

    @Override
    protected ConstructorInterceptPoint getConstructorsInterceptPoint() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                return new MethodMatcher[]{new SimpleMethodMatcher("invoke")};
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.dubbo.MonitorFilterInterceptor";
            }
        }};
    }
}

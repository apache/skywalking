package com.a.eye.skywalking.plugin.tomcat78x.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "org.apache.catalina.core.StandardEngineValve";
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
                return "com.a.eye.skywalking.plugin.tomcat78x.TomcatPluginInterceptor";
            }
        }};
    }
}

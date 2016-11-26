package com.a.eye.skywalking.plugin.tomcat78x.define;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {

        return new MethodMatcher[]{new SimpleMethodMatcher("invoke")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.tomcat78x.TomcatPluginInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        return "org.apache.catalina.core.StandardEngineValve";
    }
}

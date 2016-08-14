package com.a.eye.skywalking.plugin.tomcat78x.define;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    private static Logger logger = LogManager.getLogger(TomcatPluginDefine.class);

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

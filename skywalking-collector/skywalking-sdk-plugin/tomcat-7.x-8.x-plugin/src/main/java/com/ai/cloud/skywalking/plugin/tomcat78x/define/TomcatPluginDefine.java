package com.ai.cloud.skywalking.plugin.tomcat78x.define;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.ai.cloud.skywalking.plugin.tomcat78x.TomcatPluginInterceptor;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    private static Logger logger = LogManager.getLogger(TomcatPluginDefine.class);

    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {

        return new MethodMatcher[]{new SimpleMethodMatcher("invoke")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.tomcat78x.TomcatPluginInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        return "org.apache.catalina.core.StandardEngineValve";
    }
}

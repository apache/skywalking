package com.ai.cloud.skywalking.plugin.tomcat78x.define;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.ai.cloud.skywalking.plugin.tomcat78x.TomcatPluginInterceptor;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {

        return new MethodMatcher[]{new SimpleMethodMatcher("invoke", Request.class, Response.class)};
    }

    @Override
    protected InstanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
        return new TomcatPluginInterceptor();
    }

    @Override
    protected String enhanceClassName() {
        return "org.apache.catalina.core.StandardEngineValve";
    }
}

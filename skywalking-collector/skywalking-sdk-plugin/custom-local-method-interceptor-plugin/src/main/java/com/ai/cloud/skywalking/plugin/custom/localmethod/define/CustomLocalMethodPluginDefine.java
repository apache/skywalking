package com.ai.cloud.skywalking.plugin.custom.localmethod.define;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;

public class CustomLocalMethodPluginDefine extends ClassEnhancePluginDefine {

    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[] {new AnyMethodsMatcher()};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.custom.localmethod.CustomLocalMethodInterceptor";
    }

    @Override
    protected MethodMatcher[] getStaticMethodsMatchers() {
        return new MethodMatcher[] {new AnyMethodsMatcher()};
    }

    @Override
    protected String getStaticMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.custom.localmethod.CustomLocalMethodInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        if (!Config.Plugin.CustomLocalMethodInterceptorPlugin.IS_ENABLE){
            return null;
        }
        return Config.Plugin.CustomLocalMethodInterceptorPlugin.PACKAGE_PREFIX;
    }
}

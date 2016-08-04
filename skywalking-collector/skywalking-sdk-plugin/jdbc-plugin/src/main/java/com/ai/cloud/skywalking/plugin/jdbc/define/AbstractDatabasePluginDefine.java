package com.ai.cloud.skywalking.plugin.jdbc.define;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public abstract class AbstractDatabasePluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("connect")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.jdbc.define.DatabasePluginInterceptor";
    }
}

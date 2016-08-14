package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public abstract class AbstractDatabasePluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("connect")};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.jdbc.define.JDBCDriverInterceptor";
    }
}

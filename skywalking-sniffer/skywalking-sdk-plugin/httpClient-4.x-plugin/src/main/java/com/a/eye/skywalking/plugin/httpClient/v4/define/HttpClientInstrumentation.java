package com.a.eye.skywalking.plugin.httpClient.v4.define;


import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

/**
 * {@link HttpClientInstrumentation} present that skywalking will intercept {@link HttpClientInstrumentation#enhanceClassName()}
 * by using {@link com.a.eye.skywalking.plugin.httpClient.v4.HttpClientExecuteInterceptor}
 *
 * @author zhangxin
 */
public abstract class HttpClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Intercept class.
     */
    private static final String INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.httpClient.v4.HttpClientExecuteInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    protected String getInstanceMethodsInterceptor() {
        return INTERCEPT_CLASS;
    }
}

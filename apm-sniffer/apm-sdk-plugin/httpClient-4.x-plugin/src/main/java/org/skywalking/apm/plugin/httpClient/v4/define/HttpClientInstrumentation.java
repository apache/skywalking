package org.skywalking.apm.plugin.httpClient.v4.define;

import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.httpClient.v4.HttpClientExecuteInterceptor;

/**
 * {@link HttpClientInstrumentation} present that skywalking intercepts {@link HttpClientInstrumentation#enhanceClassName()}
 * by using {@link HttpClientExecuteInterceptor}
 *
 * @author zhangxin
 */
public abstract class HttpClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String INTERCEPT_CLASS = "org.skywalking.apm.plugin.httpClient.v4.HttpClientExecuteInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    protected String getInstanceMethodsInterceptor() {
        return INTERCEPT_CLASS;
    }
}

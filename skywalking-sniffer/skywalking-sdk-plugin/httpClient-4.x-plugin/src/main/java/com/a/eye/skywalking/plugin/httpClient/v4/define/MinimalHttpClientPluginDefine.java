package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class MinimalHttpClientPluginDefine extends HttpClientPluginDefine {
    @Override
    public String enhanceClassName() {
        return "org.apache.http.impl.client.MinimalHttpClient";
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                return new MethodMatcher[]{new SimpleMethodMatcher("doExecute")};
            }

            @Override
            public String getMethodsInterceptor() {
                return getInstanceMethodsInterceptor();
            }
        }};
    }
}

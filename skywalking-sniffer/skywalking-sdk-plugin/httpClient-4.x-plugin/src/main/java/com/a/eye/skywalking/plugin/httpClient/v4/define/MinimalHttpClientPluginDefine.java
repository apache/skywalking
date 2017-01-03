package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class MinimalHttpClientPluginDefine extends HttpClientPluginDefine {
    @Override
    public String enhanceClassName() {
        return "org.apache.http.impl.client.MinimalHttpClient";
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("doExecute");
            }

            @Override
            public String getMethodsInterceptor() {
                return getInstanceMethodsInterceptor();
            }
        }};
    }
}

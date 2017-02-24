package com.a.eye.skywalking.plugin.httpClient.v4.define;


import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link AbstractHttpClientInstrumentation} presents that skywalking will
 * intercept {@link org.apache.http.impl.client.MinimalHttpClient#doExecute(HttpHost, HttpRequest, HttpContext)}
 * by using {@link HttpClientInstrumentation#INTERCEPT_CLASS}.
 *
 * @author zhangxin
 */
public class MinimalHttpClientInstrumentation extends HttpClientInstrumentation {

    /**
     * Enhance class.
     */
    private static final String ENHANCE_CLASS = "org.apache.http.impl.client.MinimalHttpClient";

    @Override
    public String enhanceClassName() {
        return ENHANCE_CLASS;
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

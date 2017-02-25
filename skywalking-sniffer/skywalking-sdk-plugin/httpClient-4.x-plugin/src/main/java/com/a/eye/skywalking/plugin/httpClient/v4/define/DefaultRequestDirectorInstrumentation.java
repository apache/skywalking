package com.a.eye.skywalking.plugin.httpClient.v4.define;


import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link AbstractHttpClientInstrumentation} presents that skywalking intercepts
 * {@link org.apache.http.impl.client.DefaultRequestDirector#execute(HttpHost, HttpRequest, HttpContext)}
 * by using {@link HttpClientInstrumentation#INTERCEPT_CLASS}.
 *
 * @author zhangxin
 */
public class DefaultRequestDirectorInstrumentation extends HttpClientInstrumentation {

    /**
     * Enhance class.
     */
    private static final String enhanceClass = "org.apache.http.impl.client.DefaultRequestDirector";

    /**
     * DefaultRequestDirector is default implement.<br/>
     * usually use in version 4.0-4.2<br/>
     * since 4.3, this class is Deprecated.
     */
    @Override
    public String enhanceClassName() {
        return enhanceClass;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("execute");
            }

            @Override
            public String getMethodsInterceptor() {
                return getInstanceMethodsInterceptor();
            }
        }};
    }
}

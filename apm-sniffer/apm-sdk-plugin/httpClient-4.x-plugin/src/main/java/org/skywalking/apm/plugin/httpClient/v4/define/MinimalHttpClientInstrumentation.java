package org.skywalking.apm.plugin.httpClient.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link AbstractHttpClientInstrumentation} presents that skywalking
 * intercepts {@link org.apache.http.impl.client.MinimalHttpClient#doExecute(HttpHost, HttpRequest, HttpContext)}
 * by using {@link HttpClientInstrumentation#INTERCEPT_CLASS}.
 *
 * @author zhangxin
 */
public class MinimalHttpClientInstrumentation extends HttpClientInstrumentation {

    private static final String ENHANCE_CLASS = "org.apache.http.impl.client.MinimalHttpClient";

    @Override
    public String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("doExecute");
                }

                @Override
                public String getMethodsInterceptor() {
                    return getInstanceMethodsInterceptor();
                }
            }
        };
    }
}

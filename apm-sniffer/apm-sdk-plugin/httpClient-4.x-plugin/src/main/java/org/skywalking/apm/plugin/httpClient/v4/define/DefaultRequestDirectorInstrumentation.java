package org.skywalking.apm.plugin.httpClient.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link AbstractHttpClientInstrumentation} presents that skywalking intercepts
 * {@link org.apache.http.impl.client.DefaultRequestDirector#execute(HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)}
 * by using {@link HttpClientInstrumentation#INTERCEPT_CLASS}.
 *
 * @author zhangxin
 */
public class DefaultRequestDirectorInstrumentation extends HttpClientInstrumentation {

    /**
     * Enhance class.
     */
    private static final String ENHANCE_CLASS = "org.apache.http.impl.client.DefaultRequestDirector";

    /**
     * DefaultRequestDirector is default implement.<br/>
     * usually use in version 4.0-4.2<br/>
     * since 4.3, this class is Deprecated.
     */
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
                    return named("execute");
                }

                @Override
                public String getMethodsInterceptor() {
                    return getInstanceMethodsInterceptor();
                }
            }
        };
    }
}

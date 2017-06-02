package org.skywalking.apm.plugin.feign.http.v9.define;

import feign.Request;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.feign.http.v9.DefaultHttpClientInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link DefaultHttpClientInstrumentation} presents that skywalking intercepts {@link
 * feign.Client.Default#execute(Request, Request.Options)} by using {@link DefaultHttpClientInterceptor}.
 *
 * @author pengys5
 */
public class DefaultHttpClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Enhance class.
     */
    private static final String ENHANCE_CLASS = "feign.Client$Default";

    /**
     * Intercept class.
     */
    private static final String INTERCEPT_CLASS = "org.skywalking.apm.plugin.feign.http.v9.DefaultHttpClientInterceptor";

    @Override protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("execute");
                }

                @Override public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }
            }
        };
    }
}

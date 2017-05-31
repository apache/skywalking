package org.skywalking.apm.plugin.okhttp.v3.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.okhttp.v3.RealCallInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link RealCallInstrumentation} presents that skywalking intercepts {@link okhttp3.RealCall#RealCall(OkHttpClient,
 * Request, boolean)}, {@link okhttp3.RealCall#execute()} by using {@link RealCallInterceptor}.
 *
 * @author pengys5
 */
public class RealCallInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Enhance class.
     */
    private static final String ENHANCE_CLASS = "okhttp3.RealCall";

    /**
     * Intercept class.
     */
    private static final String INTERCEPT_CLASS = "org.skywalking.apm.plugin.okhttp.v3.RealCallInterceptor";

    @Override protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(OkHttpClient.class, Request.class, boolean.class);
                }

                @Override public String getConstructorInterceptor() {
                    return INTERCEPT_CLASS;
                }
            }
        };
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

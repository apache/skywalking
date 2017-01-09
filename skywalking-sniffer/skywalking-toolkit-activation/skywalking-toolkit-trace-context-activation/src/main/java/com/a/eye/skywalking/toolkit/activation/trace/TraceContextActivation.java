package com.a.eye.skywalking.toolkit.activation.trace;

import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "com.a.eye.skywalking.toolkit.trace.TraceContext".
 * Should not dependency or import any class in "skywalking-toolkit-trace-context" module.
 * Activation's classloader is diff from "com.a.eye.skywalking.toolkit.trace.TraceContext",
 * using direct will trigger classloader issue.
 * <p>
 * Created by xin on 2016/12/15.
 */
public class TraceContextActivation extends ClassStaticMethodsEnhancePluginDefine {
    /**
     * @return the target class, which needs active.
     */
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.trace.TraceContext";
    }

    /**
     * @return the collection of {@link StaticMethodsInterceptPoint}, represent the intercepted methods and their interceptors.
     */
    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {new StaticMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("traceId");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.activation.trace.TraceContextInterceptor";
            }
        }};
    }
}

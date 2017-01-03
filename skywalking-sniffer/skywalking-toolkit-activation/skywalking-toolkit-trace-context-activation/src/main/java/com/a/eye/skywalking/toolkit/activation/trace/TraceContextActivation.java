package com.a.eye.skywalking.toolkit.activation.trace;

import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Created by xin on 2016/12/15.
 */
public class TraceContextActivation extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.trace.TraceContext";
    }

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

package com.a.eye.skywalking.toolkit.activation.opentracing.tracer;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author wusheng
 */
public class SkyWalkingTracerActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.opentracing.SkyWalkingTracer";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("formatInjectCrossProcessPropagationContextData");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor.TracerInjectFormatCrossProcessContextInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("formatExtractCrossProcessPropagationContextData");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor.TracerExtractCrossProcessContextInterceptor";
                }
            }
        };
    }
}

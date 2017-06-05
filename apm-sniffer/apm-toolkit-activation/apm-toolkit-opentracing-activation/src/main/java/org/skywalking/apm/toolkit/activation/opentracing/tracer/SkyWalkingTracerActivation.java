package org.skywalking.apm.toolkit.activation.opentracing.tracer;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author wusheng
 */
public class SkyWalkingTracerActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "SkyWalkingTracer";
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
                    return "TracerInjectFormatCrossProcessContextInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("formatExtractCrossProcessPropagationContextData");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "TracerExtractCrossProcessContextInterceptor";
                }
            }
        };
    }
}

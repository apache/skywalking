package org.skywalking.apm.toolkit.activation.opentracing.span;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Created by xin on 2017/1/16.
 */
public class SkyWalkingSpanActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "SkyWalkingSpan";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(String.class, long.class, Map.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return "SpanNewInstanceInterceptor";
                }
            }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setTag");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "org.skywalking.apm.toolkit.activation.opentracing.span.interceptor.SpanSetTagInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setOperationName");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "org.skywalking.apm.toolkit.activation.opentracing.span.interceptor.SpanSetOperationNameInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("finish");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "org.skywalking.apm.toolkit.activation.opentracing.span.interceptor.SpanFinishInterceptor";
                }
            }
        };
    }
}

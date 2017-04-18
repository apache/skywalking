package com.a.eye.skywalking.toolkit.activation.opentracing.span;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;

import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Created by xin on 2017/1/16.
 */
public class SkyWalkingSpanActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpan";
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
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor.SpanNewInstanceInterceptor";
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
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor.SpanSetTagInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setOperationName");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor.SpanSetOperationNameInterceptor";
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("finish");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor.SpanFinishInterceptor";
                }
            }
        };
    }
}

package com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Created by xin on 2017/1/16.
 */
public class SkyWalkingSpanBuilderActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpanBuilder";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{
                new ConstructorInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getConstructorMatcher() {
                        return takesArguments(String.class);
                    }

                    @Override
                    public String getConstructorInterceptor() {
                        return "com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.interceptor.SpanBuilderNewInstanceInterceptor";
                    }
                }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("withTag");
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return "com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.interceptor.SpanBuilderWithTagInterceptor";
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("withStartTimestamp");
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return "com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.interceptor.SpanBuilderWithStartTimeInterceptor";
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("start");
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return "com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.interceptor.SpanBuilderStartInterceptor";
                    }
                }
        };
    }
}

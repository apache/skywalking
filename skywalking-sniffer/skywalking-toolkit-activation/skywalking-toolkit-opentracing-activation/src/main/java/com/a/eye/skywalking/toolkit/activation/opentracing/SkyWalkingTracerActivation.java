package com.a.eye.skywalking.toolkit.activation.opentracing;

import com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.nio.ByteBuffer;

import static com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Created by wusheng on 2017/1/3.
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
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("formatCrossProcessPropagationContextData");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.activation.opentracing.FormatCrossProcessContextInterceptor";
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription>  getMethodsMatcher() {
                return named("extractCrossProcessPropagationContextData").and(takesArgumentWithType(0, "io.opentracing.propagation.TextMap"));
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.activation.opentracing.ExtractCrossProcessTextMapContextInterceptor";
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription>  getMethodsMatcher() {
                return named("extractCrossProcessPropagationContextData").and(takesArgument(0, ByteBuffer.class));
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.activation.opentracing.ExtractCrossProcessByteBufferContextInterceptor";
            }
        }};
    }
}

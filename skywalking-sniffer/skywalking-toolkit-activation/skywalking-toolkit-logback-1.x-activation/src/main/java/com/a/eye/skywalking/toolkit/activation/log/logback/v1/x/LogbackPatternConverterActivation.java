package com.a.eye.skywalking.toolkit.activation.log.logback.v1.x;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Created by wusheng on 2016/12/7.
 */
public class LogbackPatternConverterActivation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.log.logback.v1.x.LogbackPatternConverter";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("convert");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.toolkit.log.logback.v1.x.PrintTraceIdInterceptor";
            }
        }};
    }
}

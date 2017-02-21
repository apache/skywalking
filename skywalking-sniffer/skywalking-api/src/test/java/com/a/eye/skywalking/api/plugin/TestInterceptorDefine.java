package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class TestInterceptorDefine extends ClassEnhancePluginDefine {

    @Override
    public String enhanceClassName() {
        return "BeInterceptedClass";
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
                return named("printabc");
            }

            @Override
            public String getMethodsInterceptor() {
                return "TestAroundInterceptor";
            }
        }};
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {new StaticMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("call");
            }

            @Override
            public String getMethodsInterceptor() {
                return "TestStaticAroundInterceptor";
            }
        }};
    }
}

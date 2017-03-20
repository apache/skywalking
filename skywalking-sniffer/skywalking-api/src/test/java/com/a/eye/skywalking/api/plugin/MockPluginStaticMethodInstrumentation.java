package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.WEAVE_CLASS;
import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.WEAVE_STATIC_METHOD_NAME;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class MockPluginStaticMethodInstrumentation extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return WEAVE_CLASS;
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[]{
                new StaticMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(WEAVE_STATIC_METHOD_NAME);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPTOR_CLASS;
                    }
                }
        };
    }
}

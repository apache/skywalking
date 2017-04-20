package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.WEAVE_CLASS;
import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.WEAVE_INSTANCE_METHOD_NAME;
import static com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefineTest.WEAVE_INSTANCE_WITH_EXCEPTION_METHOD_NAME;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class MockPluginInstanceMethodInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return WEAVE_CLASS;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, String.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPTOR_CLASS;
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
                    return named(WEAVE_INSTANCE_METHOD_NAME).and(not(AllObjectDefaultMethodsMatch.INSTANCE));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(WEAVE_INSTANCE_WITH_EXCEPTION_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }
            }
        };
    }
}

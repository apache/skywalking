package org.skywalking.apm.agent.core.plugin;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class MockPluginStaticMethodInstrumentation extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return AbstractClassEnhancePluginDefineTest.WEAVE_CLASS;
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(AbstractClassEnhancePluginDefineTest.WEAVE_STATIC_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}

package org.skywalking.apm.api.plugin;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.StaticMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.api.plugin.AbstractClassEnhancePluginDefineTest.*;

public class MockPluginStaticMethodInstrumentation extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return WEAVE_CLASS;
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
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

package org.skywalking.apm.agent.core.plugin;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class MockPluginInstanceMethodInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return AbstractClassEnhancePluginDefineTest.WEAVE_CLASS;
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
                    return AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
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
                    return named(AbstractClassEnhancePluginDefineTest.WEAVE_INSTANCE_METHOD_NAME).and(not(AllObjectDefaultMethodsMatch.INSTANCE));
                }

                @Override
                public String getMethodsInterceptor() {
                    return AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(AbstractClassEnhancePluginDefineTest.WEAVE_INSTANCE_WITH_EXCEPTION_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return AbstractClassEnhancePluginDefineTest.INTERCEPTOR_CLASS;
                }
            }
        };
    }
}

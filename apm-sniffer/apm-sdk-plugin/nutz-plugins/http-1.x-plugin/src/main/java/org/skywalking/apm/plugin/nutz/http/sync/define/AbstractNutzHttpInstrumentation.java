package org.skywalking.apm.plugin.nutz.http.sync.define;

import static net.bytebuddy.matcher.ElementMatchers.named;

import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public abstract class AbstractNutzHttpInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String DO_SEND_METHOD_NAME = "send";
    private static final String DO_SEND_INTERCEPTOR = "org.skywalking.apm.plugin.nutz.http.sync.SenderSendInterceptor";
    private static final String DO_CONSTRUCTOR_INTERCEPTOR = "org.skywalking.apm.plugin.nutz.http.sync.SenderConstructorInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return ElementMatchers.takesArguments(1);
                }

                @Override
                public String getConstructorInterceptor() {
                    return DO_CONSTRUCTOR_INTERCEPTOR;
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
                    return named(DO_SEND_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return DO_SEND_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    protected abstract ClassMatch enhanceClass();
}
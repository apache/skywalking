package org.skywalking.apm.plugin.spring.concurrent.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.plugin.spring.concurrent.FailureCallbackInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.plugin.spring.concurrent.match.FailedCallbackMatch.failedCallbackMatch;

/**
 * {@link FailureCallbackInstrumentation} enhance the onFailure method that class inherited
 * <code>org.springframework.util.concurrent.FailureCallback</code> by {@link FailureCallbackInterceptor}.
 *
 * @author zhangxin
 */
public class FailureCallbackInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String FAILURE_CALLBACK_INTERCEPTOR = "org.skywalking.apm.plugin.spring.concurrent.FailureCallbackInterceptor";
    public static final String FAILURE_METHOD_NAME = "onFailure";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(FAILURE_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return FAILURE_CALLBACK_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return failedCallbackMatch();
    }
}

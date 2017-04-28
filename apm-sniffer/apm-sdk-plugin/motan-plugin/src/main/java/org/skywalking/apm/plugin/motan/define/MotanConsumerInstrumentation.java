package org.skywalking.apm.plugin.motan.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.motan.MotanProviderInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link MotanConsumerInstrumentation} presents that skywalking intercept
 * {@link com.weibo.api.motan.cluster.support.ClusterSpi#call(com.weibo.api.motan.rpc.Request)} by using {@link MotanProviderInterceptor}.
 *
 * @author zhangxin
 */
public class MotanConsumerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.weibo.api.motan.transport.ProviderMessageRouter";

    private static final String INVOKE_INTERCEPT_CLASS = "org.skywalking.apm.plugin.motan.MotanProviderInterceptor";

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

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
                    return named("call");
                }

                @Override
                public String getMethodsInterceptor() {
                    return INVOKE_INTERCEPT_CLASS;
                }
            }
        };
    }
}

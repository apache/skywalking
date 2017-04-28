package org.skywalking.apm.plugin.dubbo;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link DubboInstrumentation} presents that skywalking intercepts {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(com.alibaba.dubbo.rpc.Invoker,
 * com.alibaba.dubbo.rpc.Invocation)} by using {@link DubboInterceptor}.
 *
 * @author zhangxin
 */
public class DubboInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.alibaba.dubbo.monitor.support.MonitorFilter";
    private static final String INTERCEPT_CLASS = "org.skywalking.apm.plugin.dubbo.DubboInterceptor";

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("invoke");
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }
            }
        };
    }
}

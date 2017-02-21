package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link DubboInstrumentation} present that skywalking use class {@link DubboInterceptor} to
 * enhance class {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)}
 * for support trace of the dubbo framework.
 *
 * @author zhangxin
 */
public class DubboInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Enhance class
     */
    private static final String ENHANCE_CLASS = "com.alibaba.dubbo.monitor.support.MonitorFilter";
    /**
     * Intercept class
     */
    private static final String INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.dubbo.DubboInterceptor";

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
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("invoke");
            }

            @Override
            public String getMethodsInterceptor() {
                return INTERCEPT_CLASS;
            }
        }};
    }
}

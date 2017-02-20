package com.a.eye.skywalking.api.plugin.dubbo;

import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * The {@link DubboPluginDefine} weave {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)}.
 *
 * @author zhangxin
 */
public class DubboPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.alibaba.dubbo.monitor.support.MonitorFilter";
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
                return "com.a.eye.skywalking.plugin.dubbo.MonitorFilterInterceptor";
            }
        }};
    }
}

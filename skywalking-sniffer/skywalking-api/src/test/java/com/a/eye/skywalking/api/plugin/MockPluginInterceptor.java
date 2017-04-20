package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

public class MockPluginInterceptor implements InstanceMethodsAroundInterceptor, StaticMethodsAroundInterceptor, InstanceConstructorInterceptor {
    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
        return ret + "_STATIC";
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        return ret + String.valueOf(context.get("VALUE"));
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {
    }

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set("VALUE", interceptorContext.allArguments()[0]);
    }
}

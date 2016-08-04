package com.ai.cloud.skywalking.plugin.jdbc.define;

import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.ai.cloud.skywalking.plugin.jdbc.SWConnection;

import java.sql.Connection;
import java.util.Properties;

public class DatabasePluginInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        return new SWConnection((String) interceptorContext.allArguments()[0],
                (Properties) interceptorContext.allArguments()[1], (Connection) ret);
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext, Object ret) {

    }
}

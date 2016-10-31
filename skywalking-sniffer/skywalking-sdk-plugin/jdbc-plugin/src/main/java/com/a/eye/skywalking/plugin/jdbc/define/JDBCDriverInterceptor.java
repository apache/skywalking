package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.jdbc.SWConnection;

import java.sql.Connection;
import java.util.Properties;

public class JDBCDriverInterceptor implements InstanceMethodsAroundInterceptor {
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
            InstanceMethodInvokeContext interceptorContext) {

    }

}

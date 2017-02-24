package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.jdbc.SWConnection;

import java.sql.Connection;
import java.util.Properties;

/**
 * {@link JDBCDriverInterceptor} will return {@link SWConnection} when {@link java.sql.Driver#connect(String, Properties)},
 * instead of the instance that extend {@link Connection}.
 *
 * @author zhangxin
 */
public class JDBCDriverInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        // do nothing
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
        // do nothing.
    }

}

package org.skywalking.apm.plugin.jdbc.define;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.plugin.jdbc.SWConnection;

import java.sql.Connection;
import java.util.Properties;

/**
 * {@link JDBCDriverInterceptor} return {@link SWConnection} when {@link java.sql.Driver} to create connection,
 * instead of the  {@link Connection} instance.
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

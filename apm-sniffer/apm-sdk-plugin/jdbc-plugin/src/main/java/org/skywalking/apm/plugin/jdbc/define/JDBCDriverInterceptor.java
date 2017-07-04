package org.skywalking.apm.plugin.jdbc.define;

import java.sql.Connection;
import java.util.Properties;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.plugin.jdbc.SWConnection;

/**
 * {@link JDBCDriverInterceptor} return {@link SWConnection} when {@link java.sql.Driver} to create connection,
 * instead of the  {@link Connection} instance.
 *
 * @author zhangxin
 */
public class JDBCDriverInterceptor implements InstanceMethodsAroundInterceptor {

    @Override public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return new SWConnection((String)allArguments[0],
            (Properties)allArguments[1], (Connection)ret);
    }

    @Override public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

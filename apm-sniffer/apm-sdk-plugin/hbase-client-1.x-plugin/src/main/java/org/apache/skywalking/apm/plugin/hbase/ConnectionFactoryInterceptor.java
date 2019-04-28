package org.apache.skywalking.apm.plugin.hbase;

import java.lang.reflect.Method;
import org.apache.hadoop.conf.Configuration;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 * @author zhangbin
 */

public class ConnectionFactoryInterceptor implements StaticMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(ConnectionFactoryInterceptor.class);

    @Override public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {
        Configuration configuration = (Configuration)allArguments[0];
        String remotePeer = configuration.get("hadoop.registry.zk.quorum");
        LOGGER.info("remotePeer is {}!", remotePeer);
        ConnectionInfo.REMOTE_PEER = remotePeer;
    }

    @Override public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {

    }
}
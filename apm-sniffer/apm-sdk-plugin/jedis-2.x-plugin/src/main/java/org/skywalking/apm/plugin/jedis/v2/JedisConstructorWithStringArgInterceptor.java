package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} record the host
 * and port information from {@link EnhancedClassInstanceContext#context}.
 *
 * @author zhangxin
 */
public class JedisConstructorWithStringArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String host = (String) interceptorContext.allArguments()[0];
        int port = 6379;
        if (interceptorContext.allArguments().length > 1) {
            port = (Integer) interceptorContext.allArguments()[1];
        }
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, host + ":" + port);
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOST, host);
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_PORT, port);
    }

}

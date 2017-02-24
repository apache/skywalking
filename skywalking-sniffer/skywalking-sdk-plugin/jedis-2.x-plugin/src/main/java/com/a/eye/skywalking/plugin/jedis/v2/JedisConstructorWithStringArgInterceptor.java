package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} will record the host and port information that fetch
 * from string argument into {@link EnhancedClassInstanceContext#context}.
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

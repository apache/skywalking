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
        String redisConnInfo;
        redisConnInfo = (String) interceptorContext.allArguments()[0];
        if (interceptorContext.allArguments().length > 1) {
            redisConnInfo += ":" + interceptorContext.allArguments()[1];
        }
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, redisConnInfo);
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOSTS, interceptorContext.allArguments()[0]);
    }

}

package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import redis.clients.jedis.HostAndPort;

import static com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO;
import static com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor.KEY_OF_REDIS_HOSTS;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} will record the host and port information that fetch
 * from {@link HostAndPort} into {@link EnhancedClassInstanceContext#context}, and each host and port will spilt ;.
 *
 * @author zhangxin
 */
public class JedisClusterConstructorWithHostAndPortArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        StringBuilder redisConnInfo = new StringBuilder();
        HostAndPort hostAndPort = (HostAndPort) interceptorContext.allArguments()[0];
        redisConnInfo.append(hostAndPort.toString()).append(";");
        context.set(KEY_OF_REDIS_CONN_INFO, redisConnInfo.toString());
        context.set(KEY_OF_REDIS_HOSTS, hostAndPort.getHost());
    }
}

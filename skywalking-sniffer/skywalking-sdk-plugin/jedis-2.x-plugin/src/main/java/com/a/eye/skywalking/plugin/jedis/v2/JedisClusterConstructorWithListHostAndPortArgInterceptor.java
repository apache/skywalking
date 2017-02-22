package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.util.Set;

import redis.clients.jedis.HostAndPort;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} will record the host and port information that fetch
 * from {@link HostAndPort} collector into {@link EnhancedClassInstanceContext#context}, and each host and port will spilt ;.
 *
 * @author zhangxin
 */
public class JedisClusterConstructorWithListHostAndPortArgInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        StringBuilder redisConnInfo = new StringBuilder();
        StringBuilder redisHost = new StringBuilder();
        Set<HostAndPort> hostAndPorts = (Set<HostAndPort>) interceptorContext.allArguments()[0];
        for (HostAndPort hostAndPort : hostAndPorts) {
            redisConnInfo.append(hostAndPort.toString()).append(";");
            redisHost.append(hostAndPort.getHost()).append(";");
        }
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, redisConnInfo.toString());
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOSTS, redisHost);
    }
}

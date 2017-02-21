package com.a.eye.skywalking.api.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;

import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.HostAndPort;

import java.util.Set;

import static com.a.eye.skywalking.api.plugin.jedis.v2.JedisMethodInterceptor.REDIS_CONN_INFO_KEY;

/**
 * Created by xin on 16-6-12.
 */
public class JedisClusterConstructorInterceptor4SetArg implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        StringBuilder redisConnInfo = new StringBuilder();
        Set<HostAndPort> hostAndPorts = (Set<HostAndPort>) interceptorContext.allArguments()[0];
        for (HostAndPort hostAndPort : hostAndPorts) {
            redisConnInfo.append(hostAndPort.toString()).append(";");
        }
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo.toString());
    }
}

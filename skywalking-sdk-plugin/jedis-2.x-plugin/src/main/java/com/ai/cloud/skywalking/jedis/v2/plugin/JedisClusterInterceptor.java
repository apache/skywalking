package com.ai.cloud.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import redis.clients.jedis.HostAndPort;

import java.util.Set;

/**
 * Created by xin on 16-6-12.
 */
public class JedisClusterInterceptor extends JedisBaseInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        StringBuilder redisConnInfo = new StringBuilder();
        if (interceptorContext.allArguments().length > 0) {
            if (interceptorContext.allArguments()[0] instanceof Set) {
                Set<HostAndPort> hostAndPorts = (Set<HostAndPort>) interceptorContext.allArguments()[0];
                for (HostAndPort hostAndPort : hostAndPorts) {
                    redisConnInfo.append(hostAndPort.toString()).append(";");
                }
            } else if (interceptorContext.allArguments()[0] instanceof HostAndPort) {
                HostAndPort hostAndPort = (HostAndPort) interceptorContext.allArguments()[0];
                redisConnInfo.append(hostAndPort.toString()).append(";");
            }
        }
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo.toString());
    }
}

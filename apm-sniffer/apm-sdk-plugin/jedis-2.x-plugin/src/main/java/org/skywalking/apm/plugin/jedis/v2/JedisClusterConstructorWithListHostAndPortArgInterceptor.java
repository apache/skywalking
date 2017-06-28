package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.HostAndPort;

import java.util.Set;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} record the host and port information that fetch
 * from {@link EnhancedClassInstanceContext#context}, and each host and port will spilt ;.
 *
 * @author zhangxin
 */
public class JedisClusterConstructorWithListHostAndPortArgInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        StringBuilder redisConnInfo = new StringBuilder();
        Set<HostAndPort> hostAndPorts = (Set<HostAndPort>) interceptorContext.allArguments()[0];
        for (HostAndPort hostAndPort : hostAndPorts) {
            redisConnInfo.append(hostAndPort.toString()).append(";");
        }
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, redisConnInfo.toString());
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOSTS, redisConnInfo.toString());
    }
}

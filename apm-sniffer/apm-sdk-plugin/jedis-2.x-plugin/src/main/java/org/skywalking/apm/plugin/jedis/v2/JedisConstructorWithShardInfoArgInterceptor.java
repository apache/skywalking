package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.JedisShardInfo;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} will record the host
 * and port information from {@link EnhancedClassInstanceContext#context}.
 *
 * @author zhangxin
 */
public class JedisConstructorWithShardInfoArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String redisConnInfo;
        JedisShardInfo shardInfo = (JedisShardInfo) interceptorContext.allArguments()[0];
        redisConnInfo = shardInfo.getHost() + ":" + shardInfo.getPort();
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, redisConnInfo);
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOST, shardInfo.getHost());
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_PORT, shardInfo.getPort());
    }
}

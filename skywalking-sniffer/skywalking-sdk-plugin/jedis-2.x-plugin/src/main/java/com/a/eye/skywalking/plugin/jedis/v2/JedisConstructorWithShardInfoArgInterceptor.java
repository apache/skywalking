package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import redis.clients.jedis.JedisShardInfo;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} will record the host and port information that fetch
 * from {@link JedisShardInfo} argument into {@link EnhancedClassInstanceContext#context}.
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
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOSTS, shardInfo.getHost());
    }
}

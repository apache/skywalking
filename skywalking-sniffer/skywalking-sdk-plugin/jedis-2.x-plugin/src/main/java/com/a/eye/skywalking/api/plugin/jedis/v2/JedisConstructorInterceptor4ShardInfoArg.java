package com.a.eye.skywalking.api.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.JedisShardInfo;

import static com.a.eye.skywalking.api.plugin.jedis.v2.JedisMethodInterceptor.REDIS_CONN_INFO_KEY;

/**
 * Created by wusheng on 2016/12/1.
 */
public class JedisConstructorInterceptor4ShardInfoArg implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String redisConnInfo;
        JedisShardInfo shardInfo = (JedisShardInfo) interceptorContext.allArguments()[0];
        redisConnInfo = shardInfo.getHost() + ":" + shardInfo.getPort();
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo);
    }
}

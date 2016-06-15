package com.ai.cloud.skywalking.jedis.v2.plugin;

import java.net.URI;

import redis.clients.jedis.JedisShardInfo;

import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

public class JedisInterceptor extends JedisBaseInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context,
                            ConstructorInvokeContext interceptorContext) {
        String redisConnInfo = "";
        if (interceptorContext.allArguments().length > 0) {
            if (interceptorContext.allArguments()[0] instanceof String) {
                redisConnInfo = (String) interceptorContext.allArguments()[0];
                if (interceptorContext.allArguments().length > 1) {
                    redisConnInfo += ":"
                            + (Integer) interceptorContext.allArguments()[1];
                }
            } else if (interceptorContext.allArguments()[0] instanceof JedisShardInfo) {
                JedisShardInfo shardInfo = (JedisShardInfo) interceptorContext
                        .allArguments()[0];
                redisConnInfo = shardInfo.getHost() + ":" + shardInfo.getPort();
            } else if (interceptorContext.allArguments()[0] instanceof URI) {
                URI uri = (URI) interceptorContext.allArguments()[0];
                redisConnInfo = uri.getHost() + ":" + uri.getPort();
            }
        }
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo);
    }

}

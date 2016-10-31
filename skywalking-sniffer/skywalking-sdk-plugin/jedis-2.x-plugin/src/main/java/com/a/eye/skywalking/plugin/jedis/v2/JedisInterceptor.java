package com.a.eye.skywalking.plugin.jedis.v2;

import java.net.URI;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import redis.clients.jedis.JedisShardInfo;

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

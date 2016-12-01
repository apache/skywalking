package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import redis.clients.jedis.JedisShardInfo;

import java.net.URI;

import static com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor.REDIS_CONN_INFO_KEY;

public class JedisConstructorInterceptor4StringArg implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String redisConnInfo;
        redisConnInfo = (String) interceptorContext.allArguments()[0];
        if (interceptorContext.allArguments().length > 1) {
            redisConnInfo += ":" + interceptorContext.allArguments()[1];
        }
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo);
    }

}

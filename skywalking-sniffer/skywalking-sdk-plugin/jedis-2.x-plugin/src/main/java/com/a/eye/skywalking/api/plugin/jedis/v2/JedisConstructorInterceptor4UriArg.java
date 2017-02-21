package com.a.eye.skywalking.api.plugin.jedis.v2;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.net.URI;

import static com.a.eye.skywalking.api.plugin.jedis.v2.JedisMethodInterceptor.REDIS_CONN_INFO_KEY;

/**
 * Created by wusheng on 2016/12/1.
 */
public class JedisConstructorInterceptor4UriArg implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String redisConnInfo;
        URI uri = (URI) interceptorContext.allArguments()[0];
        redisConnInfo = uri.getHost() + ":" + uri.getPort();
        context.set(REDIS_CONN_INFO_KEY, redisConnInfo);
    }
}

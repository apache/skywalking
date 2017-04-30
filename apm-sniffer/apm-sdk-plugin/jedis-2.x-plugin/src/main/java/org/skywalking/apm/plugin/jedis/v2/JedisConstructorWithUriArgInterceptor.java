package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ConstructorInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.net.URI;

/**
 * {@link JedisClusterConstructorWithHostAndPortArgInterceptor} record the host and port information that fetch
 * from {@link EnhancedClassInstanceContext#context}.
 *
 * @author zhangxin
 */
public class JedisConstructorWithUriArgInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        String redisConnInfo;
        URI uri = (URI) interceptorContext.allArguments()[0];
        redisConnInfo = uri.getHost() + ":" + uri.getPort();
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_CONN_INFO, redisConnInfo);
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_HOST, uri.getHost());
        context.set(JedisMethodInterceptor.KEY_OF_REDIS_PORT, uri.getPort());
    }
}

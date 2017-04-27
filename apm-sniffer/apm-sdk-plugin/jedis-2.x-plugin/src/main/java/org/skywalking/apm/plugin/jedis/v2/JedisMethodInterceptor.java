package org.skywalking.apm.plugin.jedis.v2;

import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.assist.NoConcurrencyAccessObject;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.api.util.StringUtil;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.Tags;

/**
 * {@link JedisMethodInterceptor} intercept all method of {@link redis.clients.jedis.Jedis}
 * or {@link redis.clients.jedis.JedisCluster}. {@link JedisMethodInterceptor} record
 * the redis host, operation name and the key of the operation.
 *
 * @author zhangxin
 */
public class JedisMethodInterceptor extends NoConcurrencyAccessObject implements InstanceMethodsAroundInterceptor {
    /**
     * The key name that redis connection information in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String KEY_OF_REDIS_CONN_INFO = "REDIS_CONNECTION_INFO";
    /**
     * The key name that multiple redis hosts in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String KEY_OF_REDIS_HOSTS = "KEY_OF_REDIS_HOSTS";
    /**
     * The key name that redis host in {@link EnhancedClassInstanceContext#context}.
     * it will be null if the value that fetch from {@link EnhancedClassInstanceContext#context}
     * by using {@link #KEY_OF_REDIS_HOSTS} is not null.
     */
    protected static final String KEY_OF_REDIS_HOST = "KEY_OF_REDIS_HOST";
    /**
     * The key name that redis port in {@link EnhancedClassInstanceContext#context}.
     * It can not be null if the value that fetch from {@link EnhancedClassInstanceContext#context} by
     * using {@link #KEY_OF_REDIS_HOST} is not null.
     */
    protected static final String KEY_OF_REDIS_PORT = "KEY_OF_REDIS_PORT";

    private static final String REDIS_COMPONENT = "Redis";

    @Override
    public void beforeMethod(final EnhancedClassInstanceContext context,
                             final InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        this.whenEnter(context, interceptorContext);
    }

    /**
     * set peer host information for the current active span.
     */
    private void tagPeer(Span span, EnhancedClassInstanceContext context) {
        String redisHosts = (String) context.get(KEY_OF_REDIS_HOSTS);
        if (!StringUtil.isEmpty(redisHosts)) {
            Tags.PEERS.set(span, (String) context.get(KEY_OF_REDIS_HOSTS));
        } else {
            Tags.PEER_HOST.set(span, (String) context.get(KEY_OF_REDIS_HOST));
            Tags.PEER_PORT.set(span, (Integer) context.get(KEY_OF_REDIS_PORT));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        this.whenExist(context);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        ContextManager.activeSpan().log(t);
    }

    @Override
    protected void enter(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        Span span = ContextManager.createSpan("Jedis/" + interceptorContext.methodName());
        Tags.COMPONENT.set(span, REDIS_COMPONENT);
        Tags.DB_TYPE.set(span, REDIS_COMPONENT);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        tagPeer(span, context);
        Tags.SPAN_LAYER.asDB(span);
        if (StringUtil.isEmpty(context.get(KEY_OF_REDIS_HOST, String.class))) {
            Tags.PEERS.set(span, String.valueOf(context.get(KEY_OF_REDIS_HOSTS)));
        } else {
            Tags.PEER_HOST.set(span, context.get(KEY_OF_REDIS_HOST, String.class));
            Tags.PEER_PORT.set(span, (Integer) context.get(KEY_OF_REDIS_PORT));
        }

        if (interceptorContext.allArguments().length > 0
            && interceptorContext.allArguments()[0] instanceof String) {
            Tags.DB_STATEMENT.set(span, interceptorContext.methodName() + " " + interceptorContext.allArguments()[0]);
        }
    }

    @Override
    protected void exit() {
        ContextManager.stopSpan();
    }
}

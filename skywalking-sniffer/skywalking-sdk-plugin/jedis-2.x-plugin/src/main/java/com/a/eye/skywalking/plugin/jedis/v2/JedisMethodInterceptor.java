package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.assist.NoCocurrencyAceessObject;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * {@link JedisMethodInterceptor} create span and set redis host and redis connection information
 * from {@link EnhancedClassInstanceContext#context} to span tags.
 *
 * @author zhangxin
 */
public class JedisMethodInterceptor extends NoCocurrencyAceessObject {
    /**
     * The key name that redis connection information in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String KEY_OF_REDIS_CONN_INFO = "REDIS_CONNECTION_INFO";
    /**
     * The key name that redis host in {@link EnhancedClassInstanceContext#context}.
     */
    protected static final String KEY_OF_REDIS_HOSTS = "KEY_OF_REDIS_HOSTS";

    @Override
    public void beforeMethod(final EnhancedClassInstanceContext context, final InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        this.whenEnter(context, new Runnable() {
            @Override
            public void run() {
                Span span = ContextManager.INSTANCE.createSpan(context.get(KEY_OF_REDIS_CONN_INFO, String.class) + " " + interceptorContext.methodName());
                Tags.COMPONENT.set(span, "Redis");
                Tags.PEER_HOST.set(span, (String) context.get(KEY_OF_REDIS_HOSTS));

                Tags.SPAN_LAYER.asDB(span);
                if (interceptorContext.allArguments().length > 0
                        && interceptorContext.allArguments()[0] instanceof String) {
                    span.setTag("operation_key", (String) interceptorContext.allArguments()[0]);
                }
            }
        });
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        this.whenExist(context, new Runnable() {
            @Override
            public void run() {
                ContextManager.INSTANCE.stopSpan();
            }
        });
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        ContextManager.INSTANCE.activeSpan().log(t);
    }
}

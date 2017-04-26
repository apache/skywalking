package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpan;
import io.opentracing.tag.Tags;

/**
 * Intercept these following methods:
 * {@link SkyWalkingSpan#setTag(String, boolean)}
 * {@link SkyWalkingSpan#setTag(String, Number)}
 * {@link SkyWalkingSpan#setTag(String, String)}
 */
public class SpanSetTagInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * key of {@link com.a.eye.skywalking.trace.tag.Tags#PEER_HOST}
     */
    private static final String KEY_OF_PEER_HOST_TAG = "peer.host";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
        String key = adaptTag((String)interceptorContext.allArguments()[0]);
        Object value = interceptorContext.allArguments()[1];
        if (value instanceof String)
            ContextManager.activeSpan().setTag(key, (String)value);
        else if (value instanceof Boolean)
            ContextManager.activeSpan().setTag(key, (Boolean)value);
        else if (value instanceof Integer)
            ContextManager.activeSpan().setTag(key, (Integer)value);
        else if (value instanceof Short)
            ContextManager.activeSpan().setTag(key, ((Short)value).intValue());
        else
            ContextManager.activeSpan().setTag(key, value.toString());
    }

    /**
     * Adapt {@link Tags} of open tracing.
     *
     * @return tag key
     */
    private String adaptTag(String tagKey) {
        String key = tagKey;

        if (isPeerTag(key)) {
            key = KEY_OF_PEER_HOST_TAG;
        }

        return key;
    }

    /**
     * Check current tag is peer tag.
     *
     * Skywalking put the tag value of {@link Tags#PEER_HOSTNAME}, {@link Tags#PEER_HOST_IPV4} and
     * {@link Tags#PEER_HOST_IPV6} into {@link com.a.eye.skywalking.trace.tag.Tags#PEER_HOST} which
     * facilitate analysis.
     *
     * @param key tag key
     */
    private boolean isPeerTag(String key) {
        return Tags.PEER_HOST_IPV4.equals(key) || Tags.PEER_HOST_IPV6.equals(key) || Tags.PEER_HOSTNAME.equals(key);
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {

    }
}

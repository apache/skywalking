package org.skywalking.apm.toolkit.activation.opentracing.span.interceptor;

import io.opentracing.tag.Tags;
import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingSpan;

/**
 * Intercept these following methods:
 * {@link SkyWalkingSpan#setTag(String, boolean)}
 * {@link SkyWalkingSpan#setTag(String, Number)}
 * {@link SkyWalkingSpan#setTag(String, String)}
 */
public class SpanSetTagInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * key of {@link org.skywalking.apm.trace.tag.Tags#PEER_HOST}
     */
    private static final String KEY_OF_PEER_HOST_TAG = "peer.host";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        String key = adaptTag((String) interceptorContext.allArguments()[0]);
        Object value = interceptorContext.allArguments()[1];
        if (value instanceof String)
            ContextManager.activeSpan().setTag(key, (String) value);
        else if (value instanceof Boolean)
            ContextManager.activeSpan().setTag(key, (Boolean) value);
        else if (value instanceof Integer)
            ContextManager.activeSpan().setTag(key, (Integer) value);
        else if (value instanceof Short)
            ContextManager.activeSpan().setTag(key, ((Short) value).intValue());
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
     * Check whether current tag is one of {@link Tags#PEER_HOSTNAME}, {@link Tags#PEER_HOST_IPV4} and {@link Tags#PEER_HOST_IPV6}.
     * If yes, will use the {@link org.skywalking.apm.trace.tag.Tags#PEER_HOST} as the key, instead of original key,
     * in {@link #adaptTag(String)}.
     *
     * @param key of current tag
     * @return true if this tag is host related.
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

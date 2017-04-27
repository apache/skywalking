package org.skywalking.apm.toolkit.activation.opentracing.span.interceptor;

import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingSpan;
import org.skywalking.apm.trace.Span;

import java.util.Map;

/**
 * Intercept {@link SkyWalkingSpan} constructor.
 */
public class SpanNewInstanceInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        Object[] allArguments = interceptorContext.allArguments();
        String operationName = (String) allArguments[0];
        long startTime = (Long) allArguments[1];
        Map<String, String> tags = (Map<String, String>) allArguments[2];
        Span span = ContextManager.createSpan(operationName, startTime);

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            span.setTag(entry.getKey(), entry.getValue());
        }
    }
}

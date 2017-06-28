package org.skywalking.apm.toolkit.activation.opentracing.span.interceptor;

import java.util.Map;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingSpan;

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
        AbstractSpan span = ContextManager.createSpan(operationName, startTime);

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            span.setTag(entry.getKey(), entry.getValue());
        }
    }
}

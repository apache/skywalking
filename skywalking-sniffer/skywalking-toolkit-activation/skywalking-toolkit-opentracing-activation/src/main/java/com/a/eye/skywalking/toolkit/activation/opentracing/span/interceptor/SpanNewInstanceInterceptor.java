package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpan;
import com.a.eye.skywalking.trace.Span;
import java.util.Map;

/**
 * Intercept {@link SkyWalkingSpan} constructor.
 */
public class SpanNewInstanceInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        Object[] allArguments = interceptorContext.allArguments();
        String operationName = ((String)allArguments[0]);
        long startTime = ((Long)allArguments[1]);
        Map<String, String> tags = ((Map<String, String>)allArguments[2]);
        Span span = ContextManager.createSpan(operationName, startTime);

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            span.setTag(entry.getKey(), entry.getValue());
        }
    }
}

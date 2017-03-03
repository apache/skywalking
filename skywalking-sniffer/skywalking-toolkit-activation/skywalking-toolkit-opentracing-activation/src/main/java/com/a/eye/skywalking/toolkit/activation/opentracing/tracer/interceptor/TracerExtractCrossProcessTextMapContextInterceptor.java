package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingTracer;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Intercept {@link SkyWalkingTracer#extractCrossProcessPropagationContextData(TextMap)}
 */
public class TracerExtractCrossProcessTextMapContextInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String SKY_WALKING_TRACING_NAME = "SW-TRACING-NAME";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
        // Do nothing
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        TextMap textMap = (TextMap)interceptorContext.allArguments()[0];
        Iterator<Map.Entry<String, String>> iterator = textMap.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (SKY_WALKING_TRACING_NAME.equals(entry.getKey())) {
                ContextCarrier carrier = new ContextCarrier();
                carrier.deserialize(entry.getValue());

                ContextManager.INSTANCE.extract(carrier);
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {

    }
}

package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingTracer;
import io.opentracing.propagation.TextMap;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Intercept {@link SkyWalkingTracer#extractCrossProcessPropagationContextData(TextMap)}
 */
public class TracerExtractCrossProcessByteBufferContextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        ByteBuffer byteBuffer = (ByteBuffer)interceptorContext.allArguments()[0];
        String contextDataStr = new String(byteBuffer.array(), Charset.forName("UTF-8"));

        ContextCarrier carrier = new ContextCarrier();
        carrier.deserialize(contextDataStr);

        ContextManager.extract(carrier);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {

    }
}

package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.model.RefContext;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author zhangxin
 */
public class TracerExtractCrossProcessByteBufferContextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        ByteBuffer byteBuffer = (ByteBuffer) interceptorContext.allArguments()[0];
        String contextDataStr = new String(byteBuffer.array(), Charset.forName("UTF-8"));
        Tracing.initRefContext(new RefContext(contextDataStr));
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}

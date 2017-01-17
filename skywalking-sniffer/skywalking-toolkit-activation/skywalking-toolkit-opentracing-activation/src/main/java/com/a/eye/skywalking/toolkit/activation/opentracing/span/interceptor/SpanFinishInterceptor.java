package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

import java.util.Map;

/**
 * Created by xin on 2017/1/16.
 */
public class SpanFinishInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        // do nothing
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        Span currentSpan = Tracing.getCurrentSpan();

        Map<String, String> tags = (Map<String, String>) context.get("tags");
        if (tags != null) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                Tracing.tag(currentSpan, entry.getKey(), entry.getValue());
            }
        }

        new LocalMethodInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
    }
}

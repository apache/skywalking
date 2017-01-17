package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * @author zhangxin
 */
public class SpanNewInstanceInterceptor implements InstanceConstructorInterceptor {

    private static final String OPERATION_NAME = "operationName";

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set(OPERATION_NAME, interceptorContext.allArguments()[0]);
    }
}

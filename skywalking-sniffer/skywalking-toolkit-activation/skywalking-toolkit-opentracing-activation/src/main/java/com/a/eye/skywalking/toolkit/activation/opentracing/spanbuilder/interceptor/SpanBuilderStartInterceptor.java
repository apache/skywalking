package com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.interceptor;

import com.a.eye.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.activation.opentracing.spanbuilder.OpenTracingLocalBuriedPointType;

import java.util.Map;

/**
 * @author zhangxin
 */
public class SpanBuilderStartInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String START_TIME = "startTimestamp";
    public static final String OPERATION_NAME = "operationName";
    public static final String TAGS = "tags";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {

        Long startTime = fetchStartTime(context);
        String operationName = (String) context.get(OPERATION_NAME);
        Identification.IdentificationBuilder builder = Identification.newBuilder().viewPoint(operationName)
                .spanType(OpenTracingLocalBuriedPointType.INSTANCE);

        if (startTime != null){
            builder.startTime(startTime);
        }

        Map<String, String> tags = (Map<String, String>) context.get(TAGS);
        if (tags != null) {
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                builder.tag(tag.getKey(), tag.getValue());
            }
        }

        new LocalMethodInvokeMonitor().beforeInvoke(builder.build());
    }

    private Long fetchStartTime(EnhancedClassInstanceContext context) {
        Object startTime = context.get(START_TIME);
        if (startTime != null){
            return (Long) startTime;
        }

        return null;
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}

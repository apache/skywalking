package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 2017/1/16.
 */
public class SpanSetTagInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String TAGS = "tags";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
       Map<String, String> contextTags = (Map<String, String>) context.get(TAGS);
       if (!context.isContain(TAGS)){
           contextTags = new HashMap<String, String>();
           context.set(TAGS, contextTags);
       }

        contextTags.put((String) interceptorContext.allArguments()[0], String.valueOf(interceptorContext
               .allArguments()[1]));
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}

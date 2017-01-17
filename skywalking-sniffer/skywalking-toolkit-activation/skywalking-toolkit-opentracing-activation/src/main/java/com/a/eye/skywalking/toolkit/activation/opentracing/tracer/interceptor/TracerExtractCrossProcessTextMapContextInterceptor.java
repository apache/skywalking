package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.model.RefContext;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

import java.util.Iterator;
import java.util.Map;

import io.opentracing.propagation.TextMap;

/**
 *
 * @author zhangxin
 */
public class TracerExtractCrossProcessTextMapContextInterceptor implements InstanceMethodsAroundInterceptor {


    public static final String SKY_WALKING_TRACING_NAME = "SkyWalking-TRACING-NAME";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        // Do nothing
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        TextMap textMap = (TextMap) interceptorContext.allArguments()[0];
        Iterator<Map.Entry<String, String>> iterator = textMap.iterator();
        while (iterator.hasNext()){
            Map.Entry<String, String> entry = iterator.next();
            if (SKY_WALKING_TRACING_NAME.equals(entry.getKey())){
                try {
                    Tracing.initRefContext(new RefContext(entry.getValue()));
                }catch (Throwable e){
                    // do something
                }
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}

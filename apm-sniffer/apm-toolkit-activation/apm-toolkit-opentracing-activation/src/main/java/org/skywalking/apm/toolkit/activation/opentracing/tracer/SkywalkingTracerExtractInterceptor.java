package org.skywalking.apm.toolkit.activation.opentracing.tracer;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.toolkit.opentracing.TextMapContext;

public class SkywalkingTracerExtractInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Format format = (Format)allArguments[0];
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            TextMap textMapCarrier = (TextMap)allArguments[1];

            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                Iterator<Map.Entry<String, String>> iterator = textMapCarrier.iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    if (next.getHeadKey().equals(entry.getKey())) {
                        next.setHeadValue(entry.getValue());
                        break;
                    }
                }
            }
            ContextManager.extract(contextCarrier);
        }
        return new TextMapContext();
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

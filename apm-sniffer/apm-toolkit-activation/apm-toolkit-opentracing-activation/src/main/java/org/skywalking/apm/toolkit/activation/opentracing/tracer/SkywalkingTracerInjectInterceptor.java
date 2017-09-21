package org.skywalking.apm.toolkit.activation.opentracing.tracer;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class SkywalkingTracerInjectInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Format format = (Format)allArguments[1];
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            TextMap carrier = (TextMap)allArguments[2];
            ContextCarrier contextCarrier = new ContextCarrier();
            ContextManager.inject(contextCarrier);
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                carrier.put(next.getHeadKey(), next.getHeadValue());
            }
        } else {
            //Don't support other format yet.
        }

        return null;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

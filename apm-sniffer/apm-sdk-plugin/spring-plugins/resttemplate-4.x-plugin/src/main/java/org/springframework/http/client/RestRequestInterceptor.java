package org.springframework.http.client;

import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class RestRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        AbstractAsyncClientHttpRequest clientHttpRequest = (AbstractAsyncClientHttpRequest)ret;
        if (ret != null) {
            Object[] cacheValues = (Object[])objInst.getSkyWalkingDynamicField();
            ContextCarrier contextCarrier = (ContextCarrier)cacheValues[1];
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                clientHttpRequest.getHeaders().set(next.getHeadKey(), next.getHeadValue());
            }
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

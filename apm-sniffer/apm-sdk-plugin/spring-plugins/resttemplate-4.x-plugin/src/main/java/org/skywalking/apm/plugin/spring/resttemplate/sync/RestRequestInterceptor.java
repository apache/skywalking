package org.skywalking.apm.plugin.spring.resttemplate.sync;

import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;

public class RestRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ClientHttpRequest clientHttpRequest = (ClientHttpRequest)ret;
        if (clientHttpRequest instanceof AbstractClientHttpRequest) {
            AbstractClientHttpRequest httpRequest = (AbstractClientHttpRequest)clientHttpRequest;
            ContextCarrier contextCarrier = (ContextCarrier)objInst.getSkyWalkingDynamicField();
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                httpRequest.getHeaders().set(next.getHeadKey(), next.getHeadValue());
            }
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

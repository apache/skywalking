package org.springframework.http.client;

import java.util.List;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class RestRequestInterceptor implements InstanceMethodsAroundInterceptor {
    @Override public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        AbstractAsyncClientHttpRequest clientHttpRequest = (AbstractAsyncClientHttpRequest)ret;
        clientHttpRequest.getHeaders().set(Config.Plugin.Propagation.HEADER_NAME, String.valueOf(((List<Object>)objInst.getSkyWalkingDynamicField()).get(1)));
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

package org.apache.skywalking.apm.plugin.asyncClient.v1;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.asynchttpclient.DefaultRequest;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class PrepareRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        DefaultRequest request = (DefaultRequest) allArguments[0];
        URL url = new URL(request.getUrl());
        String operationName = url.getPath();
        if (operationName == null || operationName.length() == 0) {
            operationName = "/";
        }
        ContextManager.createLocalSpan("AsyncHttpClient-Async" + operationName);
        objInst.setSkyWalkingDynamicField(ContextManager.capture());
    }

    /**
     * @param objInst
     * @param method
     * @param allArguments
     * @param argumentsTypes
     * @param ret            the method's original return value.
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws ExecutionException, InterruptedException {
        ContextManager.stopSpan();
        return ret;
    }

    /**
     * @param objInst
     * @param method
     * @param allArguments
     * @param argumentsTypes
     * @param t              the exception occur.
     */
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

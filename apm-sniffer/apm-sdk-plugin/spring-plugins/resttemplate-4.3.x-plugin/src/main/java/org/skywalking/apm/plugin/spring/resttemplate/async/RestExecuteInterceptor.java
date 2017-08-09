package org.skywalking.apm.plugin.spring.resttemplate.async;

import java.net.URI;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpMethod;

public class RestExecuteInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        final URI requestURL = (URI)allArguments[0];
        final HttpMethod httpMethod = (HttpMethod)allArguments[1];
        final ContextCarrier contextCarrier = new ContextCarrier();
        String remotePeer = requestURL.getHost() + ":" + requestURL.getPort();
        AbstractSpan span = ContextManager.createExitSpan(requestURL.getPath(), contextCarrier, remotePeer);

        span.setComponent(ComponentsDefine.REST_TEMPLATE);
        Tags.URL.set(span, requestURL.getScheme() + "://" + requestURL.getHost() + ":" + requestURL.getPort() + requestURL.getPath());
        Tags.HTTP.METHOD.set(span, httpMethod.toString());
        SpanLayer.asHttp(span);
        Object[] cacheValues = new Object[3];
        cacheValues[0] = requestURL;
        cacheValues[1] = contextCarrier.serialize();
        objInst.setSkyWalkingDynamicField(cacheValues);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Object[] cacheValues = (Object[])objInst.getSkyWalkingDynamicField();
        cacheValues[3] = ContextManager.capture();
        ((EnhancedInstance)ret).setSkyWalkingDynamicField(cacheValues);
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

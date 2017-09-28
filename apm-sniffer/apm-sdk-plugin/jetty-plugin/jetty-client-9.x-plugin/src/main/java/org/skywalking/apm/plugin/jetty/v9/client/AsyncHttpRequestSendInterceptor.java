package org.skywalking.apm.plugin.jetty.v9.client;

import java.lang.reflect.Method;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.http.HttpFields;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

public class AsyncHttpRequestSendInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        HttpRequest request = (HttpRequest)objInst;
        ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan span = ContextManager.createExitSpan(request.getURI().getPath(), contextCarrier, request.getHost() + ":" + request.getPort());
        span.setComponent(ComponentsDefine.JETTY_CLIENT);
        Tags.HTTP.METHOD.set(span, request.getMethod().asString());
        Tags.URL.set(span, request.getURI().toString());
        SpanLayer.asHttp(span);

        CarrierItem next = contextCarrier.items();
        HttpFields field = request.getHeaders();
        while (next.hasNext()) {
            next = next.next();
            field.add(next.getHeadKey(), next.getHeadValue());
        }

        EnhancedInstance callBackResult = (EnhancedInstance)allArguments[0];
        callBackResult.setSkyWalkingDynamicField(ContextManager.capture());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

package org.skywalking.apm.plugin.nutz.http.sync;

import java.lang.reflect.Method;
import java.net.URI;

import org.nutz.http.Request;
import org.nutz.http.Request.METHOD;
import org.nutz.http.Response;
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

public class SenderSendInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments, final Class<?>[] argumentsTypes,
        final MethodInterceptResult result) throws Throwable {
        Request req = (Request) objInst.getSkyWalkingDynamicField();
        final URI requestURL = req.getUrl().toURI();
        final METHOD httpMethod = req.getMethod();
        final ContextCarrier contextCarrier = new ContextCarrier();
        String remotePeer = requestURL.getHost() + ":" + requestURL.getPort();
        AbstractSpan span = ContextManager.createExitSpan(requestURL.getPath(), contextCarrier, remotePeer);
       
        span.setComponent(ComponentsDefine.NUTZ_HTTP);
        Tags.URL.set(span, requestURL.getScheme() + "://" + requestURL.getHost() + ":" + requestURL.getPort() + requestURL.getPath());
        Tags.HTTP.METHOD.set(span, httpMethod.toString());
        SpanLayer.asHttp(span);
        
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            req.getHeader().set(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments, final Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Response response = (Response)ret;
        int statusCode = response.getStatus();
        AbstractSpan span = ContextManager.activeSpan();
        if (statusCode >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override 
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

package org.skywalking.apm.plugin.nutz.http.sync;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;

import org.nutz.http.Request;
import org.nutz.http.Request.METHOD;
import org.nutz.http.Response;
import org.nutz.http.Sender;
import org.skywalking.apm.agent.core.conf.Config;
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
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        Field field = Sender.class.getDeclaredField("request");
        field.setAccessible(true);
        Request req = (Request) field.get(objInst);
        final URI requestURL = req.getUrl().toURI();
        final METHOD httpMethod = req.getMethod();
        final ContextCarrier contextCarrier = new ContextCarrier();
        String remotePeer = requestURL.getHost() + ":" + requestURL.getPort();
        AbstractSpan span = ContextManager.createExitSpan(requestURL.getPath(), contextCarrier, remotePeer);
       
        span.setComponent(ComponentsDefine.NUTZ_HTTP);
        Tags.URL.set(span, requestURL.getScheme() + "://" + requestURL.getHost() + ":" + requestURL.getPort() + requestURL.getPath());
        Tags.HTTP.METHOD.set(span, httpMethod.toString());
        SpanLayer.asHttp(span);
        
        req.getHeader().set(Config.Plugin.Propagation.HEADER_NAME, contextCarrier.serialize());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
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

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

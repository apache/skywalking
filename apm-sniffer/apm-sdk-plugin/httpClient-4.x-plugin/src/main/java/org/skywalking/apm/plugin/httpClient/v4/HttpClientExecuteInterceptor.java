package org.skywalking.apm.plugin.httpClient.v4;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
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

public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {

    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        if (allArguments[0] == null || allArguments[1] == null) {
            // illegal args, can't trace. ignore.
            return;
        }
        final HttpHost httpHost = (HttpHost)allArguments[0];
        HttpRequest httpRequest = (HttpRequest)allArguments[1];
        final ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan span = null;
        String remotePeer = httpHost.getHostName() + ":" + httpHost.getPort();
        try {
            URL url = new URL(httpRequest.getRequestLine().getUri());
            span = ContextManager.createExitSpan(url.getPath(), contextCarrier, remotePeer);
        } catch (MalformedURLException e) {
            throw e;
        }

        span.setComponent(ComponentsDefine.HTTPCLIENT);
        Tags.URL.set(span, httpRequest.getRequestLine().getUri());
        Tags.HTTP.METHOD.set(span, httpRequest.getRequestLine().getMethod());
        SpanLayer.asHttp(span);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            httpRequest.setHeader(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (allArguments[0] == null || allArguments[1] == null) {
            return ret;
        }

        HttpResponse response = (HttpResponse)ret;
        int statusCode = response.getStatusLine().getStatusCode();
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
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }
}

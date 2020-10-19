package org.apache.skywalking.apm.plugin.asyncClient.v1;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.asynchttpclient.DefaultRequest;
import org.asynchttpclient.netty.NettyResponseFuture;
import org.asynchttpclient.netty.request.NettyRequest;

import java.lang.reflect.Method;
import java.net.URL;

public class NettyRequestSenderInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        NettyResponseFuture responseFuture = (NettyResponseFuture) allArguments[0];
        NettyRequest nettyRequest = (NettyRequest) responseFuture.getNettyRequest();
        DefaultFullHttpRequest request = (DefaultFullHttpRequest) nettyRequest.getHttpRequest();

        DefaultRequest defaultHttpRequest = (DefaultRequest) responseFuture.getTargetRequest();
        URL url = new URL(defaultHttpRequest.getUrl());

        int port = url.getPort() == -1 ? 80 : url.getPort();
        String remotePeer = url.getHost() + ":" + port;
        String operationName = url.getPath();
        if (operationName == null || operationName.length() == 0) {
            operationName = "/";
        }
        AbstractSpan span = ContextManager.createExitSpan(operationName, remotePeer);
        ContextManager.continued((ContextSnapshot) objInst.getSkyWalkingDynamicField());
        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);
        span.setComponent(ComponentsDefine.ASYNC_HTTP_CLIENT);
        Tags.HTTP.METHOD.set(span, defaultHttpRequest.getMethod());
        Tags.URL.set(span, defaultHttpRequest.getUrl());
        SpanLayer.asHttp(span);

        /**
         * 增加header
         */
        DefaultHttpHeaders defaultHttpHeaders = (DefaultHttpHeaders) request.headers();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            defaultHttpHeaders.add(next.getHeadKey(), next.getHeadValue());
            defaultHttpRequest.getHeaders().add(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan abstractSpan = ContextManager.activeSpan();
        abstractSpan.errorOccurred();
        abstractSpan.log(t);
    }
}

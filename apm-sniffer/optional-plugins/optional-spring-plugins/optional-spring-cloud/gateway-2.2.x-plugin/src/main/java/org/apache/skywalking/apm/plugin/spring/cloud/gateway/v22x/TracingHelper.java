/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v22x;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v22x.define.Constants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;


public class TracingHelper {
    private static final ILog logger = LogManager.getLogger(TracingHelper.class);

    @SuppressWarnings("unchecked")
    public static AbstractTracerContext getTracingContext() {
        if (ContextManager.isActive()) {
            try {
                Field f = ContextManager.class.getDeclaredField("CONTEXT");
                f.setAccessible(true);
                ThreadLocal<AbstractTracerContext> CONTEXT = (ThreadLocal<AbstractTracerContext>) f.get(ContextManager.class);
                return CONTEXT.get();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void setTracingContext(AbstractTracerContext context) {
        try {
            Field f = ContextManager.class.getDeclaredField("CONTEXT");
            f.setAccessible(true);
            ThreadLocal<AbstractTracerContext> CONTEXT = (ThreadLocal<AbstractTracerContext>) f.get(ContextManager.class);
            CONTEXT.set(context);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void onException(Throwable cause, ServerWebExchange serverWebExchange) {
        AbstractTracerContext tracingContext = (AbstractTracerContext) serverWebExchange.getAttributes().get(Constants.ContextKey);
        if (tracingContext == null) {
            return;
        }
        tracingContext.activeSpan().errorOccurred().log(cause);
    }

    public static void onServerRequest(ServerWebExchange serverWebExchange) {
        if (ContextManager.isActive()) {
            setTracingContext(null); /* 说明上一个请求还没有处理完(server response还没有发出), 该线程又开始处理新的请求, 需要把线程变量CONTEXT设置成null, 延迟设置null是为了后面的其他plugin还可以使用ContextManager接口 */
        }
        ServerHttpRequest request = serverWebExchange.getRequest();
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            List<String> headers = request.getHeaders().get(next.getHeadKey());
            if (headers != null && headers.size() > 0) {
                next.setHeadValue(headers.get(0));
            }

        }
        AbstractSpan span = ContextManager.createEntrySpan(request.getURI().getPath(), contextCarrier);
        Tags.URL.set(span, request.getURI().getPath());
        Tags.HTTP.METHOD.set(span, request.getMethodValue());
        span.setComponent(ComponentsDefine.SPRING_CLOUD_GATEWAY);
        SpanLayer.asHttp(span);

        serverWebExchange.getAttributes().put(Constants.ContextKey, getTracingContext());

    }

    public static void onServerResponse(ServerWebExchange serverWebExchange) {

        AbstractTracerContext tracingContext = (AbstractTracerContext) serverWebExchange.getAttributes().get(Constants.ContextKey);

        if (tracingContext == null) {
            return;
        }

        AbstractSpan span = tracingContext.activeSpan();
        int scCode = serverWebExchange.getResponse().getStatusCode().value();
        if (scCode < 200 || scCode >= 400) {
            span.errorOccurred();
        }
        Tags.STATUS_CODE.set(span, String.valueOf(scCode));
        tracingContext.stopSpan(span); /*stop entryspan */
    }

    public static void onClientRequest(ServerWebExchange serverWebExchange) {
        AbstractTracerContext tracingContext = (AbstractTracerContext) serverWebExchange.getAttributes().get(Constants.ContextKey);
        ServerHttpRequest request = serverWebExchange.getRequest();

        URI url = serverWebExchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String peer = url.getHost() + ":" + url.getPort();

        ContextCarrier contextCarrier = new ContextCarrier();
        String uri = request.getURI().getPath();

        AbstractSpan span = null;
        if (tracingContext != null) {
            span = tracingContext.createExitSpan(uri, peer);
            tracingContext.inject(contextCarrier);
        } else {
            if (ContextManager.isActive()) {
                setTracingContext(null); /* 说明上一个请求还没有处理完(client response还没有收到), 该线程又开始处理新的请求, 需要把线程变量CONTEXT设置成null *//* 和下面的ContextManager.createExitSpan是一对 */
            }
            span = ContextManager.createExitSpan(uri, contextCarrier, peer);

            serverWebExchange.getAttributes().put(Constants.ContextKey, getTracingContext());
        }
        Tags.URL.set(span, peer + url.getPath());
        Tags.HTTP.METHOD.set(span, request.getMethodValue());
        span.setComponent(ComponentsDefine.SPRING_CLOUD_GATEWAY);
        SpanLayer.asHttp(span);
        CarrierItem next = contextCarrier.items();

        HttpHeaders headers = serverWebExchange.getRequest().getHeaders();


        Field field;
        try {
            field = HttpHeaders.class.getDeclaredField("headers");
            field.setAccessible(true);
            MultiValueMap<String, String> maps = (MultiValueMap<String, String>) field.get(headers);
            while (next.hasNext()) {
                next = next.next();
                maps.add(next.getHeadKey(), next.getHeadValue());
//		            request.headers().set(next.getHeadKey(), next.getHeadValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void onClientResponse(ServerWebExchange serverWebExchange) {
        AbstractTracerContext tracingContext = (AbstractTracerContext) serverWebExchange.getAttributes().get(Constants.ContextKey);
        if (tracingContext == null) {
            return;
        }
        AbstractSpan span = tracingContext.activeSpan();
        int scCode = serverWebExchange.getResponse().getStatusCode().value();
        if (scCode < 200 || scCode >= 400) {
            span.errorOccurred();
        }
        Tags.STATUS_CODE.set(span, String.valueOf(scCode));
        tracingContext.stopSpan(span); // stop exitspan
    }
}

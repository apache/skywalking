/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.webflux.v5.webclient;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.function.BiConsumer;

public class WebFluxWebClientInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInvocationContext context) throws Throwable {
        if (allArguments[0] == null) {
            //illegal args,can't trace ignore
            return;
        }

        ClientRequest request = (ClientRequest) allArguments[0];
        final ContextCarrier contextCarrier = new ContextCarrier();

        URI uri = request.url();
        final String requestURIString = getRequestURIString(uri);
        final String operationName = requestURIString;
        final String remotePeer = getIPAndPort(uri);
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, remotePeer);

        //set components name
        span.setComponent(ComponentsDefine.SPRING_WEBCLIENT);
        Tags.URL.set(span, uri.toString());
        Tags.HTTP.METHOD.set(span, request.method().toString());
        SpanLayer.asHttp(span);

        if (request instanceof EnhancedInstance) {
            ((EnhancedInstance) request).setSkyWalkingDynamicField(contextCarrier);
        }

        //user async interface
        span.prepareForAsync();
        ContextManager.stopSpan();

        context.setContext(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret, MethodInvocationContext context) throws Throwable {
        // fix the problem that allArgument[0] may be null
        if (allArguments[0] == null) {
            return ret;
        }
        Mono<ClientResponse> ret1 = (Mono<ClientResponse>) ret;
        AbstractSpan span = (AbstractSpan) context.getContext();
        return ret1.doAfterSuccessOrError(new BiConsumer<ClientResponse, Throwable>() {
            @Override
            public void accept(ClientResponse clientResponse, Throwable throwable) {
                HttpStatus httpStatus = clientResponse.statusCode();
                if (httpStatus != null) {
                    Tags.STATUS_CODE.set(span, Integer.toString(httpStatus.value()));
                    if (httpStatus.isError()) {
                        span.errorOccurred();
                    }
                }
            }
        }).doOnError(error -> {
            span.log(error);
        }).doFinally(s -> {
            span.asyncFinish();
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    private String getRequestURIString(URI uri) {
        String requestPath = uri.getPath();
        return requestPath != null && requestPath.length() > 0 ? requestPath : "/";
    }

    // return ip:port
    private String getIPAndPort(URI uri) {
        return uri.getHost() + ":" + uri.getPort();
    }
}

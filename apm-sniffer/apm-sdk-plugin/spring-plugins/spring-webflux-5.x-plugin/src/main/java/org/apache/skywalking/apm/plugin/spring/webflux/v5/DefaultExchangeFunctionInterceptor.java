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

package org.apache.skywalking.apm.plugin.spring.webflux.v5;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;

public class DefaultExchangeFunctionInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        final ClientRequest clientRequest = (ClientRequest) allArguments[0];
        final URI requestURL = clientRequest.url();
        final HttpMethod httpMethod = clientRequest.method();
        final ContextCarrier contextCarrier = new ContextCarrier();

        int port = getPort(requestURL);
        String remotePeer = requestURL.getHost() + ":" + port;
        String formatURIPath = getURIPath(requestURL);
        AbstractSpan span = ContextManager.createExitSpan(formatURIPath, contextCarrier, remotePeer);

        span.setComponent(ComponentsDefine.SPRING_WEBCLIENT);
        Tags.URL.set(span, requestURL.getScheme() + "://" + requestURL.getHost() + ":" + port + formatURIPath);
        Tags.HTTP.METHOD.set(span, httpMethod.toString());
        SpanLayer.asHttp(span);

        if (clientRequest instanceof EnhancedInstance) {
            ((EnhancedInstance) clientRequest).setSkyWalkingDynamicField(contextCarrier);
        }
        span.prepareForAsync();
        ContextManager.stopSpan(span);
        objInst.setSkyWalkingDynamicField(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        AbstractSpan span = (AbstractSpan) objInst.getSkyWalkingDynamicField();
        return ((Mono) ret).doOnSuccess(res -> {
            try {
                if (res instanceof EnhancedInstance) {
                    HttpStatus httpStatus = (HttpStatus) ((EnhancedInstance) res).getSkyWalkingDynamicField();
                    if (httpStatus != null) {
                        Tags.STATUS_CODE.set(span, Integer.toString(httpStatus.value()));
                        if (httpStatus.isError()) {
                            span.errorOccurred();
                        }
                    }
                }
            } catch (Throwable t) {
                span.log(t);
            }
        }).doOnError(error -> {
            span.errorOccurred();
            span.log((Throwable) error);
        }).doFinally(s -> span.asyncFinish());
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

    private int getPort(URI requestURL) {
        return requestURL.getPort() > 0 ? requestURL.getPort() : "https".equalsIgnoreCase(requestURL
                .getScheme()) ? 443 : 80;
    }

    private String getURIPath(URI requestURL) {
        String formatURIPath = requestURL.getPath();
        return StringUtil.isEmpty(formatURIPath) ? "/" : formatURIPath;
    }
}

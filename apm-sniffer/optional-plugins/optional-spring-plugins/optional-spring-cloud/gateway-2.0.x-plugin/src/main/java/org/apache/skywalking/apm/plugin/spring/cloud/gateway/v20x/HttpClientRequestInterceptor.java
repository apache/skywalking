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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v20x.define.EnhanceCacheObject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClientRequest;
import reactor.ipc.netty.http.client.HttpClientResponse;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.SPRING_CLOUD_GATEWAY;

public class HttpClientRequestInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.activeSpan();

        URL url = new URL((String) allArguments[1]);
        ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan abstractSpan = ContextManager.createExitSpan(
            "SpringCloudGateway/sendRequest", contextCarrier, getPeer(url));
        abstractSpan.prepareForAsync();
        Tags.URL.set(abstractSpan, String.valueOf(allArguments[1]));

        abstractSpan.setComponent(SPRING_CLOUD_GATEWAY);
        ContextManager.stopSpan(abstractSpan);

        Function<? super HttpClientRequest, ? extends Publisher<Void>> handler = (Function<? super HttpClientRequest, ? extends Publisher<Void>>) allArguments[2];
        allArguments[2] = new Function<HttpClientRequest, Publisher<Void>>() {
            @Override
            public Publisher<Void> apply(final HttpClientRequest httpClientRequest) {
                //
                CarrierItem next = contextCarrier.items();
                if (httpClientRequest instanceof EnhancedInstance) {
                    ((EnhancedInstance) httpClientRequest).setSkyWalkingDynamicField(next);
                }
                return handler.apply(httpClientRequest);
            }
        };

        objInst.setSkyWalkingDynamicField(new EnhanceCacheObject(span, abstractSpan));
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        EnhanceCacheObject enhanceCacheObject = (EnhanceCacheObject) objInst.getSkyWalkingDynamicField();
        Mono<HttpClientResponse> responseMono = (Mono<HttpClientResponse>) ret;
        return responseMono.doAfterSuccessOrError(new BiConsumer<HttpClientResponse, Throwable>() {
            @Override
            public void accept(final HttpClientResponse httpClientResponse, final Throwable throwable) {

                AbstractSpan abstractSpan = enhanceCacheObject.getSendSpan();
                if (abstractSpan != null) {
                    if (throwable != null) {
                        abstractSpan.log(throwable);
                    } else if (httpClientResponse.status().code() > 400) {
                        abstractSpan.errorOccurred();
                    }
                    Tags.STATUS_CODE.set(abstractSpan, String.valueOf(httpClientResponse.status().code()));
                    abstractSpan.asyncFinish();
                }

                objInst.setSkyWalkingDynamicField(null);
                enhanceCacheObject.getFilterSpan().asyncFinish();
            }
        });
    }

    private String getPeer(URL url) {
        return url.getHost() + ":" + url.getPort();
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {

    }
}

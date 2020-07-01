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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.function.BiFunction;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.define.EnhanceObjectCache;
import org.apache.skywalking.apm.util.StringUtil;
import org.reactivestreams.Publisher;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClientRequest;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.SPRING_CLOUD_GATEWAY;

public class HttpClientFinalizerSendInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        EnhanceObjectCache enhanceObjectCache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
        AbstractSpan span = ContextManager.activeSpan();
        span.prepareForAsync();

        if (!StringUtil.isEmpty(enhanceObjectCache.getUrl())) {
            URL url = new URL(enhanceObjectCache.getUrl());

            ContextCarrier contextCarrier = new ContextCarrier();
            AbstractSpan abstractSpan = ContextManager.createExitSpan(
                "SpringCloudGateway/sendRequest", contextCarrier, getPeer(url));
            Tags.URL.set(abstractSpan, enhanceObjectCache.getUrl());
            abstractSpan.prepareForAsync();
            abstractSpan.setComponent(SPRING_CLOUD_GATEWAY);

            ContextManager.stopSpan(abstractSpan);
            ContextManager.stopSpan(span);

            BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>> finalSender = (BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>>) allArguments[0];
            allArguments[0] = new BiFunction<HttpClientRequest, NettyOutbound, Publisher<Void>>() {
                @Override
                public Publisher<Void> apply(HttpClientRequest request, NettyOutbound outbound) {
                    Publisher publisher = finalSender.apply(request, outbound);

                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        request.requestHeaders().remove(next.getHeadKey());
                        request.requestHeaders().set(next.getHeadKey(), next.getHeadValue());
                    }
                    return publisher;
                }
            };
            enhanceObjectCache.setCacheSpan(abstractSpan);
        }

        enhanceObjectCache.setSpan1(span);
    }

    private String getPeer(URL url) {
        return url.getHost() + ":" + url.getPort();
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ((EnhancedInstance) ret).setSkyWalkingDynamicField(objInst.getSkyWalkingDynamicField());
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }
}

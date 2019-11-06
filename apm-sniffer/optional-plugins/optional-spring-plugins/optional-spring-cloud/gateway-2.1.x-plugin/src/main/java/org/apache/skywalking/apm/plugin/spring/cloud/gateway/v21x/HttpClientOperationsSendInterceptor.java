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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x;

import io.netty.handler.codec.http.HttpHeaders;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v21x.context.SWTransmitter;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.client.HttpClientRequest;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;


/**
 * @author zhaoyuguang
 */
public class HttpClientOperationsSendInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        SWTransmitter transmitter = (SWTransmitter) objInst.getSkyWalkingDynamicField();
        if (transmitter != null) {
            HttpClientRequest request = (HttpClientRequest) objInst;

            HttpHeaders header = request.requestHeaders();
            ChannelOperations channelOpt = (ChannelOperations) objInst;
            InetSocketAddress remote = (InetSocketAddress) (channelOpt.channel().remoteAddress());
            String peer = remote.getHostName() + ":" + remote.getPort();

            AbstractSpan span = ContextManager.createExitSpan(transmitter.getOperationName(), peer);
            ContextManager.continued(transmitter.getSnapshot());
            ContextCarrier contextCarrier = new ContextCarrier();
            ContextManager.inject(contextCarrier);

            span.setComponent(ComponentsDefine.SPRING_CLOUD_GATEWAY);
            Tags.URL.set(span, peer + request.uri());
            Tags.HTTP.METHOD.set(span, request.method().name());
            SpanLayer.asHttp(span);

            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                header.set(next.getHeadKey(), next.getHeadValue());
            }
            transmitter.setSpanGateway(span.prepareForAsync());
            ContextManager.stopSpan(span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }


    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

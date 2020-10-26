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
 */

package org.apache.skywalking.apm.plugin.asyncClientHttp.v1;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.apm.plugin.asyncClientHttp.v1.wrapper.AsyncCompletionHandlerWrapper;
import org.asynchttpclient.DefaultRequest;
import org.asynchttpclient.netty.NettyResponseFuture;

import java.lang.reflect.Method;
import java.net.URL;

public class RealRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        NettyResponseFuture responseFuture = (NettyResponseFuture) allArguments[0];
        responseFuture.setAsyncHandler(new AsyncCompletionHandlerWrapper());
        allArguments[0] = responseFuture;
        DefaultRequest defaultHttpRequest = (DefaultRequest) responseFuture.getTargetRequest();
        URL url = new URL(defaultHttpRequest.getUrl());

        int port = url.getPort() == -1 ? 80 : url.getPort();
        String remotePeer = url.getHost() + ":" + port;
        String operationName = url.getPath();
        if (operationName == null || operationName.length() == 0) {
            operationName = "/";
        }
        AbstractSpan span = ContextManager.createExitSpan(operationName, remotePeer);
        if (objInst.getSkyWalkingDynamicField() != null) {
            ContextManager.continued((ContextSnapshot) objInst.getSkyWalkingDynamicField());
        }
        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);
        span.setComponent(new OfficialComponent(102, "AsyncHttpClient"));
        Tags.HTTP.METHOD.set(span, defaultHttpRequest.getMethod());
        Tags.URL.set(span, defaultHttpRequest.getUrl());
        SpanLayer.asHttp(span);

        DefaultHttpHeaders defaultHttpHeaders = (DefaultHttpHeaders) defaultHttpRequest.getHeaders();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            defaultHttpHeaders.add(next.getHeadKey(), next.getHeadValue());
            defaultHttpRequest.getHeaders().add(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}

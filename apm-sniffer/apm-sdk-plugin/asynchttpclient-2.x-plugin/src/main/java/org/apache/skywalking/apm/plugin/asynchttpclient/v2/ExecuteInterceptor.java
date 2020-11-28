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

package org.apache.skywalking.apm.plugin.asynchttpclient.v2;

import io.netty.handler.codec.http.HttpHeaders;
import java.lang.reflect.Method;
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
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;
import org.asynchttpclient.uri.Uri;

/**
 * interceptor for {@link org.asynchttpclient.DefaultAsyncHttpClient}
 */
public class ExecuteInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        final Request httpRequest = (Request) allArguments[0];
        final Uri requestUri = httpRequest.getUri();

        AbstractSpan span = ContextManager.createExitSpan(
            "AsyncHttpClient" + requestUri.getPath(), requestUri.getHost() + ":" + requestUri.getPort());

       //We wrapped the allArguments[1] for get the real time duration, and stop the span.
        allArguments[1] = new AsyncHandlerWrapper((AsyncHandler) allArguments[1], span);

        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);
        span.setComponent(ComponentsDefine.ASYNC_HTTP_CLIENT);
        Tags.HTTP.METHOD.set(span, httpRequest.getMethod());
        Tags.URL.set(span, httpRequest.getUrl());
        SpanLayer.asHttp(span);

       //Wait the span async stop.
        span.prepareForAsync();

        final HttpHeaders headers = httpRequest.getHeaders();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            headers.set(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
        ContextManager.activeSpan().asyncFinish();
    }
}

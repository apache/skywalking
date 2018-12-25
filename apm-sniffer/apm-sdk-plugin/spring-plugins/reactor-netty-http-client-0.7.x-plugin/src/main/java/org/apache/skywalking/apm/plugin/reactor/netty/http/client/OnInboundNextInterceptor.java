/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.handler.codec.http.HttpRequest;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.*;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.*;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class OnInboundNextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        HttpRequest httpRequest = (HttpRequest)ContextManager.getRuntimeContext().get("SW_NETTY_HTTP_CLIENT_REQUEST");
        if (httpRequest != null) {
            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(httpRequest.headers().get(next.getHeadKey()));
            }

            AbstractSpan span = ContextManager.createExitSpan(httpRequest.uri(), contextCarrier, httpRequest.uri());
            Tags.URL.set(span, httpRequest.uri());
            Tags.HTTP.METHOD.set(span, httpRequest.method().name());
            span.setComponent(ComponentsDefine.NETTY_HTTP);
            SpanLayer.asHttp(span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}

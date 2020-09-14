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

package org.apache.skywalking.apm.plugin.vertx3;

import io.vertx.core.http.HttpClientRequest;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class HttpClientRequestImplInterceptor implements InstanceMethodsAroundInterceptor {

    static class HttpClientRequestContext {
        String remotePeer;
        boolean usingWebClient;
        VertxContext vertxContext;
        boolean sent;

        HttpClientRequestContext(String remotePeer) {
            this.remotePeer = remotePeer;
        }
    }

    public static class Version30XTo33XConstructorInterceptor implements InstanceConstructorInterceptor {
        @Override
        public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
            String host = (String) allArguments[2];
            int port = (Integer) allArguments[3];
            objInst.setSkyWalkingDynamicField(new HttpClientRequestContext(host + ":" + port));
        }
    }

    public static class Version34XTo37XConstructorInterceptor implements InstanceConstructorInterceptor {
        @Override
        public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
            String host = (String) allArguments[3];
            int port = (Integer) allArguments[4];
            objInst.setSkyWalkingDynamicField(new HttpClientRequestContext(host + ":" + port));
        }
    }

    public static class Version38PlusConstructorInterceptor implements InstanceConstructorInterceptor {
        @Override
        public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
            String host = (String) allArguments[4];
            int port = (Integer) allArguments[5];
            objInst.setSkyWalkingDynamicField(new HttpClientRequestContext(host + ":" + port));
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) {
        HttpClientRequestContext requestContext = (HttpClientRequestContext) objInst.getSkyWalkingDynamicField();
        if (!requestContext.sent) {
            HttpClientRequest request = (HttpClientRequest) objInst;
            ContextCarrier contextCarrier = new ContextCarrier();
            AbstractSpan span = ContextManager.createExitSpan(toPath(request.uri()), contextCarrier,
                    requestContext.remotePeer);
            span.setComponent(ComponentsDefine.VERTX);
            SpanLayer.asHttp(span);
            Tags.HTTP.METHOD.set(span, request.method().toString());
            Tags.URL.set(span, request.uri());

            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                request.headers().add(next.getHeadKey(), next.getHeadValue());
            }
            requestContext.vertxContext = new VertxContext(ContextManager.capture(), span.prepareForAsync());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        HttpClientRequestContext requestContext = (HttpClientRequestContext) objInst.getSkyWalkingDynamicField();
        if (!requestContext.sent) {
            requestContext.sent = true;
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private static String toPath(String uri) {
        int index = uri.indexOf("?");
        if (index > -1) {
            return uri.substring(0, index);
        } else {
            return uri;
        }
    }
}

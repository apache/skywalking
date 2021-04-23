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

package org.apache.skywalking.apm.plugin.okhttp.v3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
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

/**
 * {@link AsyncCallInterceptor} get the `EnhanceRequiredInfo` instance from `SkyWalkingDynamicField` and then put it
 * into `AsyncCall` instance when the `AsyncCall` constructor called.
 * <p>
 * {@link AsyncCallInterceptor} also create an exit span by using the `EnhanceRequiredInfo` when the `execute` method
 * called.
 */
public class AsyncCallInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        /**
         * The first argument of constructor is not the `real` parameter when the enhance class is an inner class. This
         * is the JDK compiler mechanism.
         */
        EnhancedInstance realCallInstance = (EnhancedInstance) allArguments[1];
        Object enhanceRequireInfo = realCallInstance.getSkyWalkingDynamicField();

        objInst.setSkyWalkingDynamicField(enhanceRequireInfo);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        EnhanceRequiredInfo enhanceRequiredInfo = (EnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        Request request = (Request) enhanceRequiredInfo.getRealCallEnhance().getSkyWalkingDynamicField();

        HttpUrl requestUrl = request.url();
        AbstractSpan span = ContextManager.createExitSpan(requestUrl.uri()
                                                                    .getPath(), requestUrl.host() + ":" + requestUrl.port());
        ContextManager.continued(enhanceRequiredInfo.getContextSnapshot());
        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);
        span.setComponent(ComponentsDefine.OKHTTP);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, requestUrl.uri().toString());
        SpanLayer.asHttp(span);

        Field headersField = Request.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Headers.Builder headerBuilder = request.headers().newBuilder();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            headerBuilder.set(next.getHeadKey(), next.getHeadValue());
        }
        headersField.set(request, headerBuilder.build());

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}

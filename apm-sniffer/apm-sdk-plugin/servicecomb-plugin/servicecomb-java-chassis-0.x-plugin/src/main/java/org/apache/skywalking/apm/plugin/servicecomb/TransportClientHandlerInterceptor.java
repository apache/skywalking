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

package org.apache.skywalking.apm.plugin.servicecomb;

import io.servicecomb.core.Invocation;
import java.lang.reflect.Method;
import java.net.URI;
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

public class TransportClientHandlerInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        Invocation invocation = (Invocation) allArguments[0];
        if (!checkRegisterStatus(invocation)) {
            return;
        }
        URI uri = new URI(invocation.getEndpoint().toString());
        String peer = uri.getHost() + ":" + uri.getPort();
        String operationName = invocation.getMicroserviceQualifiedName();
        final ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, peer);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            invocation.getContext().put(next.getHeadKey(), next.getHeadValue());
        }
        String url = invocation.getOperationMeta().getOperationPath();
        Tags.URL.set(span, url);
        span.setComponent(ComponentsDefine.SERVICECOMB);
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Invocation invocation = (Invocation) allArguments[0];
        if (!checkRegisterStatus(invocation)) {
            return ret;
        }
        AbstractSpan span = ContextManager.activeSpan();
        int statusCode = invocation.getStatus().getStatusCode();
        if (statusCode >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        Invocation invocation = (Invocation) allArguments[0];
        if (!checkRegisterStatus(invocation)) {
            return;
        }
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    /**
     * Serviecomb chassis Consumers and providers need to register at the service center. If the consumer is not
     * registered then return false.
     */
    private Boolean checkRegisterStatus(Invocation invocation) {
        return null != invocation.getOperationMeta() && null != invocation.getEndpoint();
    }

}

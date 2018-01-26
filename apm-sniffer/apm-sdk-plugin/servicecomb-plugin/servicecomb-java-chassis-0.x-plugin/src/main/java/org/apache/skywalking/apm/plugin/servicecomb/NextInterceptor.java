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
import io.servicecomb.swagger.invocation.InvocationType;
import io.servicecomb.swagger.invocation.SwaggerInvocation;
import io.servicecomb.swagger.invocation.context.InvocationContext;
import java.lang.reflect.Method;
import java.net.URI;
import javax.ws.rs.core.Response.StatusType;
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

/**
 * {@link NextInterceptor} define how to enhance class {@link Invocation#next(io.servicecomb.swagger.invocation.AsyncResponse)}.
 *
 * @author lytscu
 */
public class NextInterceptor implements InstanceMethodsAroundInterceptor {
    static final ThreadLocal DEEP = new ThreadLocal() {
        @Override
        protected Integer initialValue() {
            Integer deepindex = 0;
            return deepindex;
        }
    };

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        SwaggerInvocation swagger = (SwaggerInvocation)objInst;
        InvocationType type = swagger.getInvocationType();
        Invocation invocation = (Invocation)objInst;
        AbstractSpan span;
        boolean isConsumer = type.equals(InvocationType.CONSUMER);
        if (isConsumer) {
            Integer count = (Integer)DEEP.get();
            try {
                //When count = 2, you can get the peer
                if (count == 2) {
                    URI uri = new URI(invocation.getEndpoint().toString());
                    String peer = uri.getHost() + ":" + uri.getPort();
                    final ContextCarrier contextCarrier = new ContextCarrier();
                    span = ContextManager.createExitSpan(invocation.getInvocationQualifiedName(), contextCarrier, peer);
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
            } finally {
                count++;
                DEEP.set(count);
            }
        }
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        SwaggerInvocation swagger = (SwaggerInvocation)objInst;
        InvocationType type = swagger.getInvocationType();
        boolean isConsumer = type.equals(InvocationType.CONSUMER);
        if (isConsumer) {
            Integer count = (Integer)DEEP.get();
            try {
                if (count == 1) {
                    AbstractSpan span = ContextManager.activeSpan();
                    StatusType statusType = ((InvocationContext)objInst).getStatus();
                    int statusCode = statusType.getStatusCode();
                    if (statusCode >= 400) {
                        span.errorOccurred();
                        Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
                    }
                    ContextManager.stopSpan();
                }
            } finally {
                count--;
                DEEP.set(count);
            }
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(t);
    }

}

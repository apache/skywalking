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

package org.apache.skywalking.apm.plugin.armeria;

import com.linecorp.armeria.client.UserClient;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import io.netty.util.AsciiString;
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

import java.lang.reflect.Method;
import java.net.URI;

@SuppressWarnings("rawtypes")
public class Armeria084ClientInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) throws Throwable {

        final UserClient userClient = (UserClient) objInst;
        final URI uri = userClient.uri();
        final HttpMethod httpMethod = (HttpMethod) allArguments[1];
        final String path = (String) allArguments[2];
        final Object req = allArguments[5];

        if (!(req instanceof HttpRequest)) {
            return;
        }

        final HttpRequest httpReq = (HttpRequest) req;

        final ContextCarrier contextCarrier = new ContextCarrier();
        final String remotePeer = uri.getHost() + ":" + uri.getPort();

        final AbstractSpan exitSpan = ContextManager.createExitSpan(path, contextCarrier, remotePeer);

        exitSpan.setComponent(ComponentsDefine.ARMERIA);
        exitSpan.setLayer(SpanLayer.HTTP);
        Tags.HTTP.METHOD.set(exitSpan, httpMethod.name());

        HttpHeaders headers = httpReq.headers();
        for (CarrierItem item = contextCarrier.items(); item.hasNext(); ) {
            item = item.next();
            headers.add(AsciiString.of(item.getHeadKey()), item.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {

        Object req = allArguments[5];

        if (req instanceof HttpRequest && ContextManager.isActive()) {
            ContextManager.stopSpan();
        }

        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}

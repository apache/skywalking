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

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.common.HttpHeadersBuilder;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.util.SafeCloseable;
import io.netty.util.AsciiString;
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
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public abstract class ArmeriaClientInterceptor implements InstanceMethodsAroundInterceptor {
    private static final String KEY_SAFE_CLOSEABLE = "SAFE_CLOSEABLE";

    protected void beforeMethod(final URI uri, final HttpMethod httpMethod, final String path) {
        final ContextCarrier contextCarrier = new ContextCarrier();
        final String remotePeer = uri.getHost() + ":" + uri.getPort();

        final AbstractSpan exitSpan = ContextManager.createExitSpan(path, contextCarrier, remotePeer);

        exitSpan.setComponent(ComponentsDefine.ARMERIA);
        exitSpan.setLayer(SpanLayer.HTTP);
        Tags.HTTP.METHOD.set(exitSpan, httpMethod.name());

        ContextManager.getRuntimeContext().put(KEY_SAFE_CLOSEABLE, Clients.withHttpHeaders(headers -> {
            HttpHeadersBuilder builder = headers.toBuilder();
            for (CarrierItem item = contextCarrier.items(); item.hasNext(); ) {
                item = item.next();
                builder.add(AsciiString.of(item.getHeadKey()), item.getHeadValue());
            }
            return builder.build();
        }));
    }

    protected void afterMethod(final Object req) {
        if (req instanceof HttpRequest && ContextManager.isActive()) {
            ContextManager.stopSpan();
        }

        SafeCloseable safeCloseable = ContextManager.getRuntimeContext().get(KEY_SAFE_CLOSEABLE, SafeCloseable.class);
        if (safeCloseable != null) {
            safeCloseable.close();
        }
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                                      final Class<?>[] argumentsTypes, final Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}

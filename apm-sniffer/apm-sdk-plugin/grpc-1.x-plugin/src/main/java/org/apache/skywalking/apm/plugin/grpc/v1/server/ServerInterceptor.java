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

package org.apache.skywalking.apm.plugin.grpc.v1.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil;
import org.apache.skywalking.apm.util.StringUtil;

public class ServerInterceptor implements io.grpc.ServerInterceptor {
    @Override
    public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(ServerCall<REQUEST, RESPONSE> call,
        Metadata headers, ServerCallHandler<REQUEST, RESPONSE> handler) {
        final ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            String contextValue = headers.get(Metadata.Key.of(next.getHeadKey(), Metadata.ASCII_STRING_MARSHALLER));
            if (!StringUtil.isEmpty(contextValue)) {
                next.setHeadValue(contextValue);
            }
        }

        final AbstractSpan span = ContextManager.createEntrySpan(OperationNameFormatUtil.formatOperationName(call.getMethodDescriptor()), contextCarrier);
        span.setComponent(ComponentsDefine.GRPC);
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
        try {
            return new TracingServerCallListener<>(handler.startCall(new TracingServerCall<>(call, ContextManager.capture()), headers), call
                .getMethodDescriptor(), ContextManager.capture());
        } finally {
            ContextManager.stopSpan();
        }
    }
}

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

package org.apache.skywalking.apm.plugin.grpc.v1.client;

import io.grpc.*;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.BLOCKING_CALL_EXIT_SPAN;
import static org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil.formatOperationName;

/**
 * Simple tracing client for internal gRPC server which have server tracing plugin,
 * it just create a ExitSpan, more tracing info will be create by server.
 *
 * @author kanro
 */
class SimpleTracingClientCall<REQUEST, RESPONSE> extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final String serviceName;
    private final String remotePeer;

    SimpleTracingClientCall(ClientCall<REQUEST, RESPONSE> delegate, MethodDescriptor<REQUEST, RESPONSE> method, Channel channel) {
        super(delegate);

        this.serviceName = formatOperationName(method);
        this.remotePeer = channel.authority();
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
        final AbstractSpan blockingSpan = (AbstractSpan) ContextManager.getRuntimeContext().get(BLOCKING_CALL_EXIT_SPAN);
        final ContextCarrier contextCarrier = new ContextCarrier();

        if (blockingSpan == null) {
            final AbstractSpan span = ContextManager.createExitSpan(serviceName, remotePeer);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);
        }

        ContextManager.inject(contextCarrier);
        CarrierItem contextItem = contextCarrier.items();
        while (contextItem.hasNext()) {
            contextItem = contextItem.next();
            Metadata.Key<String> headerKey = Metadata.Key.of(contextItem.getHeadKey(), Metadata.ASCII_STRING_MARSHALLER);
            headers.put(headerKey, contextItem.getHeadValue());
        }

        try {
            delegate().start(responseListener, headers);
        } catch (Throwable t) {
            ContextManager.activeSpan().errorOccurred().log(t);
            throw t;
        } finally {
            ContextManager.stopSpan();
        }
    }
}

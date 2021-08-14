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

import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.BLOCKING_CALL_EXIT_SPAN;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.CLIENT;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.REQUEST_ON_CANCEL_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.REQUEST_ON_COMPLETE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.REQUEST_ON_MESSAGE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.RESPONSE_ON_CLOSE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.RESPONSE_ON_MESSAGE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil.formatOperationName;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import javax.annotation.Nullable;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil;

/**
 * Fully client tracing for gRPC servers.
 */
class TracingClientCall<REQUEST, RESPONSE> extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final String serviceName;
    private final String remotePeer;
    private final String operationPrefix;
    private final MethodDescriptor<REQUEST, RESPONSE> methodDescriptor;
    private ContextSnapshot snapshot;

    TracingClientCall(ClientCall<REQUEST, RESPONSE> delegate, MethodDescriptor<REQUEST, RESPONSE> method,
        Channel channel) {
        super(delegate);

        this.methodDescriptor = method;
        this.serviceName = formatOperationName(method);
        this.remotePeer = channel.authority();
        this.operationPrefix = OperationNameFormatUtil.formatOperationName(method) + CLIENT;
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
        final AbstractSpan blockingSpan = (AbstractSpan) ContextManager.getRuntimeContext()
                                                                       .get(BLOCKING_CALL_EXIT_SPAN);
        final ContextCarrier contextCarrier = new ContextCarrier();

        // Avoid create ExitSpan repeatedly, ExitSpan of blocking calls will create by BlockingCallInterceptor.
        if (blockingSpan == null) {
            final AbstractSpan span = ContextManager.createExitSpan(serviceName, remotePeer);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);
        } else {
            ContextManager.getRuntimeContext().remove(BLOCKING_CALL_EXIT_SPAN);
        }

        ContextManager.inject(contextCarrier);
        CarrierItem contextItem = contextCarrier.items();
        while (contextItem.hasNext()) {
            contextItem = contextItem.next();
            Metadata.Key<String> headerKey = Metadata.Key.of(contextItem.getHeadKey(), Metadata.ASCII_STRING_MARSHALLER);
            headers.put(headerKey, contextItem.getHeadValue());
        }

        snapshot = ContextManager.capture();
        try {
            delegate().start(new TracingClientCallListener(responseListener, snapshot), headers);
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            if (blockingSpan == null) {
                ContextManager.stopSpan();
            }
        }
    }

    @Override
    public void sendMessage(REQUEST message) {
        if (methodDescriptor.getType().clientSendsOneMessage()) {
            super.sendMessage(message);
            return;
        }

        final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + REQUEST_ON_MESSAGE_OPERATION_NAME);
        span.setComponent(ComponentsDefine.GRPC);
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
        ContextManager.continued(snapshot);

        try {
            super.sendMessage(message);
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            ContextManager.stopSpan();
        }
    }

    @Override
    public void halfClose() {
        final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + REQUEST_ON_COMPLETE_OPERATION_NAME);
        span.setComponent(ComponentsDefine.GRPC);
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
        ContextManager.continued(snapshot);

        try {
            super.halfClose();
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            ContextManager.stopSpan();
        }
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + REQUEST_ON_CANCEL_OPERATION_NAME);
        span.setComponent(ComponentsDefine.GRPC);
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
        ContextManager.continued(snapshot);

        if (cause != null) {
            span.log(cause);
        }

        try {
            super.cancel(message, cause);
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            ContextManager.stopSpan();
        }
    }

    class TracingClientCallListener extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RESPONSE> {
        private final ContextSnapshot contextSnapshot;

        TracingClientCallListener(Listener<RESPONSE> delegate, ContextSnapshot contextSnapshot) {
            super(delegate);
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public void onMessage(RESPONSE message) {
            if (methodDescriptor.getType().serverSendsOneMessage()) {
                super.onMessage(message);
                return;
            }

            final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + RESPONSE_ON_MESSAGE_OPERATION_NAME);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);
            ContextManager.continued(contextSnapshot);

            try {
                delegate().onMessage(message);
            } catch (Throwable t) {
                ContextManager.activeSpan().log(t);
            } finally {
                ContextManager.stopSpan();
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + RESPONSE_ON_CLOSE_OPERATION_NAME);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);
            ContextManager.continued(contextSnapshot);
            if (!status.isOk()) {
                span.log(status.asRuntimeException());
                Tags.RPC_RESPONSE_STATUS_CODE.set(span, status.getCode().name());
            }

            try {
                delegate().onClose(status, trailers);
            } catch (Throwable t) {
                ContextManager.activeSpan().log(t);
            } finally {
                ContextManager.stopSpan();
            }
        }
    }
}

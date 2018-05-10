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

package org.apache.skywalking.apm.plugin.grpc.v1;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.CLIENT;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.STREAM_RESPONSE_OBSERVER_ON_COMPLETE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.STREAM_RESPONSE_OBSERVER_ON_ERROR_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.STREAM_RESPONSE_OBSERVER_ON_NEXT_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil.formatOperationName;

/**
 * @author zhangxin
 */
public class StreamCallClientInterceptor extends ForwardingClientCall.SimpleForwardingClientCall {

    private final String serviceName;
    private final String remotePeer;
    private final String operationPrefix;

    protected StreamCallClientInterceptor(ClientCall delegate, MethodDescriptor method, Channel channel) {
        super(delegate);
        this.serviceName = formatOperationName(method);
        this.remotePeer = channel.authority();
        this.operationPrefix = OperationNameFormatUtil.formatOperationName(method) + CLIENT;
    }

    @Override
    public void start(Listener responseListener, Metadata headers) {
        final ContextCarrier contextCarrier = new ContextCarrier();
        final AbstractSpan span = ContextManager.createExitSpan(serviceName, contextCarrier, remotePeer);
        span.setComponent(ComponentsDefine.GRPC);
        SpanLayer.asRPCFramework(span);
        CarrierItem contextItem = contextCarrier.items();
        while (contextItem.hasNext()) {
            contextItem = contextItem.next();
            Metadata.Key<String> headerKey = Metadata.Key.of(contextItem.getHeadKey(), Metadata.ASCII_STRING_MARSHALLER);
            headers.put(headerKey, contextItem.getHeadValue());
        }
        delegate().start(new CallListener(responseListener, ContextManager.capture()), headers);
        ContextManager.stopSpan();
    }

    private class CallListener extends ForwardingClientCallListener.SimpleForwardingClientCallListener {

        private final ContextSnapshot contextSnapshot;

        protected CallListener(Listener delegate, ContextSnapshot contextSnapshot) {
            super(delegate);
            this.contextSnapshot = contextSnapshot;
        }

        @Override public void onReady() {
            delegate().onReady();
        }

        @Override public void onHeaders(Metadata headers) {
            delegate().onHeaders(headers);
        }

        @Override public void onMessage(Object message) {
            try {
                ContextManager.createLocalSpan(operationPrefix + STREAM_RESPONSE_OBSERVER_ON_NEXT_OPERATION_NAME);
                ContextManager.continued(contextSnapshot);
                delegate().onMessage(message);
            } catch (Throwable t) {
                ContextManager.activeSpan().errorOccurred().log(t);
            } finally {
                ContextManager.stopSpan();
            }
        }

        @Override public void onClose(Status status, Metadata trailers) {
            try {
                if (!status.isOk()) {
                    AbstractSpan abstractSpan = ContextManager.createLocalSpan(operationPrefix + STREAM_RESPONSE_OBSERVER_ON_ERROR_OPERATION_NAME);
                    abstractSpan.errorOccurred().log(status.getCause());
                    Tags.STATUS_CODE.set(abstractSpan, status.getCode().name());
                } else {
                    AbstractSpan abstractSpan = ContextManager.createLocalSpan(operationPrefix + STREAM_RESPONSE_OBSERVER_ON_COMPLETE_OPERATION_NAME);
                }
                delegate().onClose(status, trailers);
                ContextManager.continued(contextSnapshot);
            } catch (Throwable t) {
                ContextManager.activeSpan().errorOccurred().log(t);
            } finally {
                ContextManager.stopSpan();
            }
        }
    }

}

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

import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.RESPONSE_ON_CLOSE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.RESPONSE_ON_MESSAGE_OPERATION_NAME;
import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.SERVER;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.grpc.v1.OperationNameFormatUtil;

public class TracingServerCall<REQUEST, RESPONSE> extends ForwardingServerCall.SimpleForwardingServerCall<REQUEST, RESPONSE> {

    private final String operationPrefix;

    protected TracingServerCall(ServerCall<REQUEST, RESPONSE> delegate) {
        super(delegate);
        this.operationPrefix = OperationNameFormatUtil.formatOperationName(delegate.getMethodDescriptor()) + SERVER;
    }

    @Override
    public void sendMessage(RESPONSE message) {
        // We just create the request on message span for server stream calls.
        if (!getMethodDescriptor().getType().serverSendsOneMessage()) {
            final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + RESPONSE_ON_MESSAGE_OPERATION_NAME);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);

            try {
                super.sendMessage(message);
            } catch (Throwable t) {
                ContextManager.activeSpan().log(t);
                throw t;
            } finally {
                ContextManager.stopSpan();
            }
        } else {
            super.sendMessage(message);
        }
    }

    @Override
    public void close(Status status, Metadata trailers) {
        final AbstractSpan span = ContextManager.createLocalSpan(operationPrefix + RESPONSE_ON_CLOSE_OPERATION_NAME);
        span.setComponent(ComponentsDefine.GRPC);
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
        switch (status.getCode()) {
            case OK:
                break;
            // UNKNOWN/INTERNAL status code will case error in this span.
            // Those status code means some unexpected error occurred in server.
            // Similar to 5XX in HTTP status.
            case UNKNOWN:
            case INTERNAL:
                if (status.getCause() == null) {
                    span.log(status.asRuntimeException());
                } else {
                    span.log(status.getCause());
                }
                break;
            // Other status code means some predictable error occurred in server.
            // Like PERMISSION_DENIED or UNAUTHENTICATED somethings.
            // Similar to 4XX in HTTP status.
            default:
                // But if the status still has cause exception, we will log it too.
                if (status.getCause() != null) {
                    span.log(status.getCause());
                }
                break;
        }
        Tags.RPC_RESPONSE_STATUS_CODE.set(span, status.getCode().name());

        try {
            super.close(status, trailers);
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            ContextManager.stopSpan();
        }
    }

}

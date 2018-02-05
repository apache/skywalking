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

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.plugin.grpc.v1.Constants.STREAM_ON_NEXT_OPERATION_NAME;

/**
 * @author zhang xin
 */
public class CallServerInterceptor implements ServerInterceptor {
    @Override
    public ServerCall.Listener interceptCall(ServerCall call, Metadata headers, ServerCallHandler handler) {
        Map<String, String> headerMap = new HashMap<String, String>();
        for (String key : headers.keys()) {
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                headerMap.put(key, value);
            }
        }

        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            String contextValue = headerMap.get(next.getHeadKey());
            if (!StringUtil.isEmpty(contextValue)) {
                next.setHeadValue(contextValue);
            }
        }

        final AbstractSpan span = ContextManager.createEntrySpan(OperationNameFormatUtil.formatOperationName(call.getMethodDescriptor()), contextCarrier);
        span.setComponent(ComponentsDefine.GRPC);

        return new ServerCallListener(handler.startCall(new ForwardingServerCall.SimpleForwardingServerCall(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                delegate().sendHeaders(responseHeaders);
            }
        }, headers), call.getMethodDescriptor());
    }

    public class ServerCallListener extends ForwardingServerCallListener.SimpleForwardingServerCallListener {

        protected ServerCallListener(ServerCall.Listener delegate, MethodDescriptor descriptor) {
            super(delegate);
        }

        @Override public void onReady() {
            delegate().onReady();
        }

        @Override public void onMessage(Object message) {
            try {
                ContextManager.createLocalSpan(STREAM_ON_NEXT_OPERATION_NAME);
                delegate().onMessage(message);
            } catch (Throwable t) {
                ContextManager.activeSpan().errorOccurred().log(t);
            } finally {
                ContextManager.stopSpan();
            }
        }

        @Override public void onComplete() {
            delegate().onComplete();
            ContextManager.stopSpan();
        }

        @Override public void onCancel() {
            delegate().onCancel();
            ContextManager.stopSpan();
        }

        @Override public void onHalfClose() {
            delegate().onHalfClose();
        }
    }

}

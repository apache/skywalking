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

package org.apache.skywalking.apm.testcase.grpc.consumr;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsumerInterceptor implements ClientInterceptor {

    private static final Logger LOGGER = LogManager.getLogger(ConsumerInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> descriptor,
        CallOptions options, Channel channel) {
        LOGGER.info("start interceptor!");
        LOGGER.info("method type: {}", descriptor.getType());
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(descriptor, options)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                LOGGER.info("Peer: {}", channel.authority());
                LOGGER.info("Operation Name : {}", descriptor.getFullMethodName());
                Interceptor<RespT> tracingResponseListener = new Interceptor(responseListener);
                tracingResponseListener.contextSnapshot = "contextSnapshot";
                delegate().start(tracingResponseListener, headers);
            }

            @Override
            public void cancel(@Nullable String message, @Nullable Throwable cause) {
                LOGGER.info("cancel");
                super.cancel(message, cause);
            }

            @Override
            public void halfClose() {
                LOGGER.info("halfClose");
                super.halfClose();
            }

            @Override
            public void sendMessage(ReqT message) {
                LOGGER.info("sendMessage ....");
                super.sendMessage(message);
            }
        };
    }

    private static class Interceptor<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
        private static final Logger LOGGER = LogManager.getLogger(Interceptor.class);

        private Object contextSnapshot;

        protected Interceptor(ClientCall.Listener<RespT> delegate) {
            super(delegate);
        }

        @Override
        public void onHeaders(Metadata headers) {
            LOGGER.info("on Headers");
            for (String key : headers.keys()) {
                LOGGER.info("Receive key: {}", key);
            }
            delegate().onHeaders(headers);
        }

        @Override
        public void onMessage(RespT message) {
            LOGGER.info("contextSnapshot: {}", contextSnapshot);
            delegate().onMessage(message);
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            LOGGER.info("on close");
            delegate().onClose(status, trailers);
        }

        @Override
        public void onReady() {
            LOGGER.info("on Ready");
            super.onReady();
        }
    }
}

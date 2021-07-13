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

package org.apache.skywalking.apm.testcase.grpc.provider.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProviderInterceptor implements ServerInterceptor {
    private static final Logger LOGGER = LogManager.getLogger(ProviderInterceptor.class);

    @Override
    public <REQ_T, RESQ_T> ServerCall.Listener<REQ_T> interceptCall(ServerCall<REQ_T, RESQ_T> call, Metadata metadata,
                                                                    ServerCallHandler<REQ_T, RESQ_T> handler) {
        Map<String, String> headerMap = new HashMap<String, String>();
        for (String key : metadata.keys()) {
            LOGGER.info("Receive key: {}", key);
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                String value = metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

                headerMap.put(key, value);
            }
        }
        LOGGER.info("authority : {}", call.getAuthority());
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<REQ_T>(handler.startCall(new ForwardingServerCall.SimpleForwardingServerCall<REQ_T, RESQ_T>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                LOGGER.info("sendHeaders....");
                Metadata.Key<String> headerKey = Metadata.Key.of("test-server", Metadata.ASCII_STRING_MARSHALLER);
                responseHeaders.put(headerKey, "test-server");
                delegate().sendHeaders(responseHeaders);
            }

            @Override
            public void sendMessage(RESQ_T message) {
                delegate().sendMessage(message);
            }

        }, metadata)) {
            @Override
            public void onReady() {
                LOGGER.info("onReady....");
                delegate().onReady();
            }

            @Override
            public void onCancel() {
                LOGGER.info("onCancel....");
                delegate().onCancel();
            }

            @Override
            public void onComplete() {
                LOGGER.info("onComplete....");
                delegate().onComplete();
            }

            @Override
            public void onHalfClose() {
                LOGGER.info("onHalfClose....");
                delegate().onHalfClose();
            }

            @Override
            public void onMessage(REQ_T message) {
                LOGGER.info("onMessage....");
                delegate().onMessage(message);
            }
        };
    }
}

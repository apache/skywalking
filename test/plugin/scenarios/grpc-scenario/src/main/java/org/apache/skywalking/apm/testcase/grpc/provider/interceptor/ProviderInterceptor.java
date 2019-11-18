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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ProviderInterceptor implements ServerInterceptor {
    private Logger logger = LogManager.getLogger(ProviderInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata,
        ServerCallHandler<ReqT, RespT> handler) {
        Map<String, String> headerMap = new HashMap<String, String>();
        for (String key : metadata.keys()) {
            logger.info("Receive key: {}", key);
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                String value = metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

                headerMap.put(key, value);
            }
        }
        logger.info("authority : {}", call.getAuthority());
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(handler.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                logger.info("sendHeaders....");
                Metadata.Key<String> headerKey = Metadata.Key.of("test-server", Metadata.ASCII_STRING_MARSHALLER);
                responseHeaders.put(headerKey, "test-server");
                delegate().sendHeaders(responseHeaders);
            }

            @Override public void sendMessage(RespT message) {
                delegate().sendMessage(message);
            }

        }, metadata)) {
            @Override public void onReady() {
                logger.info("onReady....");
                delegate().onReady();
            }

            @Override public void onCancel() {
                logger.info("onCancel....");
                delegate().onCancel();
            }

            @Override public void onComplete() {
                logger.info("onComplete....");
                delegate().onComplete();
            }

            @Override public void onHalfClose() {
                logger.info("onHalfClose....");
                delegate().onHalfClose();
            }

            @Override public void onMessage(ReqT message) {
                logger.info("onMessage....");
                delegate().onMessage(message);
            }
        };
    }
}

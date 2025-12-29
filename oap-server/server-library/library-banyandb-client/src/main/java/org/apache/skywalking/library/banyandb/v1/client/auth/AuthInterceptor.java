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

package org.apache.skywalking.library.banyandb.v1.client.auth;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class AuthInterceptor implements ClientInterceptor {
    private final String username;
    private final String password;

    private static final Metadata.Key<String> USERNAME_KEY =
            Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PASSWORD_KEY =
            Metadata.Key.of("password", Metadata.ASCII_STRING_MARSHALLER);

    public AuthInterceptor(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public <REQ_T, RESP_T> ClientCall<REQ_T, RESP_T> interceptCall(
            MethodDescriptor<REQ_T, RESP_T> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<REQ_T, RESP_T>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RESP_T> responseListener, Metadata headers) {
                headers.put(USERNAME_KEY, username);
                headers.put(PASSWORD_KEY, password);

                super.start(responseListener, headers);
            }
        };
    }
}
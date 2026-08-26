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
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Attaches the basic-auth credentials to every outgoing RPC.
 *
 * <p>The credentials are read per call instead of being captured once, so {@link #updateCredentials}
 * applies to the next RPC without rebuilding the gRPC channel. That is what lets the storage plugin
 * pick up a rotated secrets management file while OAP keeps serving.
 *
 * <p>This interceptor is installed unconditionally, even when no credentials are configured: it sends
 * no headers until both a username and a password are set, so authentication can also be turned on at
 * runtime rather than only at boot.
 */
public class AuthInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> USERNAME_KEY =
            Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PASSWORD_KEY =
            Metadata.Key.of("password", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * The username and password are swapped together through this single reference, so a rotation can
     * never leave an RPC sending a new username paired with the previous password.
     */
    private volatile Credentials credentials;

    public AuthInterceptor(String username, String password) {
        this.credentials = new Credentials(username, password);
    }

    /**
     * Replace the credentials carried by subsequent RPCs. This interceptor owns the values that go on
     * the wire; the {@code Options} the client was built with keep the boot-time configuration.
     *
     * @param username the new username; when either half is blank no credentials are sent at all
     * @param password the new password; when either half is blank no credentials are sent at all
     */
    public void updateCredentials(String username, String password) {
        this.credentials = new Credentials(username, password);
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
                final Credentials current = credentials;
                if (current.isComplete()) {
                    headers.put(USERNAME_KEY, current.username);
                    headers.put(PASSWORD_KEY, current.password);
                }

                super.start(responseListener, headers);
            }
        };
    }

    private static final class Credentials {
        private final String username;
        private final String password;

        private Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        private boolean isComplete() {
            return StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password);
        }
    }
}

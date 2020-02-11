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

package org.apache.skywalking.oap.server.core.server.auth;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Active the authentication between agent and oap receiver. token checker if expected token exists in application.yml
 */
public class AuthenticationInterceptor implements ServerInterceptor {

    private String expectedToken = "";

    private ServerCall.Listener listener = new ServerCall.Listener() {
    };

    public AuthenticationInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public void setExpectedToken(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    private static final Metadata.Key<String> AUTH_HEAD_HEADER_NAME = Metadata.Key.of("Authentication", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * intercept point of call.
     *
     * @param serverCall        call of server.
     * @param metadata          of call.
     * @param serverCallHandler handler of call.
     * @param <REQUEST>         of call.
     * @param <RESPONSE>        of call.
     * @return lister of call.
     */
    @Override
    public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(ServerCall<REQUEST, RESPONSE> serverCall,
        Metadata metadata, ServerCallHandler<REQUEST, RESPONSE> serverCallHandler) {
        String token = metadata.get(AUTH_HEAD_HEADER_NAME);
        if (expectedToken.equals(token)) {
            return serverCallHandler.startCall(serverCall, metadata);
        } else {
            serverCall.close(Status.PERMISSION_DENIED, new Metadata());
            return listener;
        }
    }
}

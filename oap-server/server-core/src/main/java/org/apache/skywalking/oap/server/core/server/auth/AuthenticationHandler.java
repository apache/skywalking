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

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;

/**
 * Active the authentication(agent <---> oap) token checker if expected token exists in application.yml
 *
 * @author wusheng, jian.tan
 */
public enum AuthenticationHandler {
    INSTANCE;

    private static final Metadata.Key<String> AUTH_HEAD_HEADER_NAME = Metadata.Key.of("Authentication", Metadata.ASCII_STRING_MARSHALLER);

    private String expectedToken = "";

    public void build(GRPCServer grpcHandler, BindableService targetService) {
        if (StringUtil.isNotEmpty(expectedToken)) {
            grpcHandler.addHandler(ServerInterceptors.intercept(targetService, new ServerInterceptor() {
                @Override
                public <REQ, RESP> ServerCall.Listener<REQ> interceptCall(ServerCall<REQ, RESP> serverCall,
                    Metadata metadata,
                    ServerCallHandler<REQ, RESP> next) {
                    String token = metadata.get(AUTH_HEAD_HEADER_NAME);
                    if (expectedToken.equals(token)) {
                        return next.startCall(serverCall, metadata);
                    } else {
                        serverCall.close(Status.PERMISSION_DENIED, new Metadata());
                        return new ServerCall.Listener() {
                        };
                    }
                }
            }));
        } else {
            grpcHandler.addHandler(targetService);
        }
    }

    public void setExpectedToken(String expectedToken) {
        this.expectedToken = expectedToken;
    }
}
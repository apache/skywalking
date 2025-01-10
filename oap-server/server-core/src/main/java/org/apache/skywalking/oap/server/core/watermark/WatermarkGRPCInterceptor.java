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

package org.apache.skywalking.oap.server.core.watermark;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * gRPCWatermarkInterceptor is a gRPC interceptor that checks if the watermark is exceeded before processing the request.
 */
public class WatermarkGRPCInterceptor extends WatermarkListener implements ServerInterceptor {
    public static WatermarkGRPCInterceptor INSTANCE;

    private WatermarkGRPCInterceptor() {
        super("gRPC-Watermark-Interceptor");
    }

    public static WatermarkGRPCInterceptor create() {
        INSTANCE = new WatermarkGRPCInterceptor();
        return INSTANCE;
    }

    @Override
    public <REQ, RESP> ServerCall.Listener<REQ> interceptCall(final ServerCall<REQ, RESP> call,
                                                              final Metadata headers,
                                                              final ServerCallHandler<REQ, RESP> next) {
        if (isWatermarkExceeded()) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("Watermark exceeded"), new Metadata());
            return new ServerCall.Listener<REQ>() {
            };
        }

        ServerCall.Listener<REQ> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<REQ>(delegate) {
            @Override
            public void onMessage(final REQ message) {
                if (isWatermarkExceeded()) {
                    call.close(Status.RESOURCE_EXHAUSTED.withDescription("Watermark exceeded"), new Metadata());
                    return;
                }

                super.onMessage(message);
            }
        };
    }
}

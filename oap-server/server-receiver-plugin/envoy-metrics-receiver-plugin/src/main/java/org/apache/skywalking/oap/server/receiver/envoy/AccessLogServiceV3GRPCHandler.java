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

package org.apache.skywalking.oap.server.receiver.envoy;

import io.envoyproxy.envoy.service.accesslog.v3.AccessLogServiceGrpc;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

@Slf4j
public class AccessLogServiceV3GRPCHandler extends AccessLogServiceGrpc.AccessLogServiceImplBase {
    private final AccessLogServiceGRPCHandler handler;

    public AccessLogServiceV3GRPCHandler(final ModuleManager manager, final EnvoyMetricReceiverConfig config) throws ModuleStartException {
        handler = new AccessLogServiceGRPCHandler(manager, config);
    }

    public StreamObserver<StreamAccessLogsMessage> streamAccessLogs(final StreamObserver<StreamAccessLogsResponse> responseObserver) {
        return new StreamObserver<StreamAccessLogsMessage>() {
            {
                handler.reset();
            }

            @Override
            public void onNext(StreamAccessLogsMessage message) {
                handler.handle(message);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error in receiving access log from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamAccessLogsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}

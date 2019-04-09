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

import io.envoyproxy.envoy.service.accesslog.v2.*;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

public class AccessLogServiceGRPCHandler extends AccessLogServiceGrpc.AccessLogServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(AccessLogServiceGRPCHandler.class);

    public AccessLogServiceGRPCHandler(ModuleManager manager) {

    }

    public StreamObserver<StreamAccessLogsMessage> streamAccessLogs(
        StreamObserver<StreamAccessLogsResponse> responseObserver) {
        return new StreamObserver<StreamAccessLogsMessage>() {
            @Override public void onNext(StreamAccessLogsMessage message) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Received msg {}", message);
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error("Error in receiving access log from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override public void onCompleted() {
                responseObserver.onNext(StreamAccessLogsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}

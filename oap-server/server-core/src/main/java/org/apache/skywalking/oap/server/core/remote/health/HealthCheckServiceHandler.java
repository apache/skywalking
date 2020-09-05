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

package org.apache.skywalking.oap.server.core.remote.health;

import grpc.health.v1.HealthCheckService;
import grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckServiceHandler extends HealthGrpc.HealthImplBase implements GRPCHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckServiceHandler.class);

    /**
     * By my test, consul didn't send the service.
     *
     * @param request          service
     * @param responseObserver status
     */
    @Override
    public void check(HealthCheckService.HealthCheckRequest request,
        StreamObserver<HealthCheckService.HealthCheckResponse> responseObserver) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received the gRPC server health check with the service name of {}", request.getService());
        }

        HealthCheckService.HealthCheckResponse.Builder response = HealthCheckService.HealthCheckResponse.newBuilder();
        response.setStatus(HealthCheckService.HealthCheckResponse.ServingStatus.SERVING);

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}

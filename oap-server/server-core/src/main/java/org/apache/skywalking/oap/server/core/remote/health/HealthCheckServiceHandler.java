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

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

@Slf4j
public class HealthCheckServiceHandler extends HealthGrpc.HealthImplBase implements GRPCHandler {
    /**
     * By my test, consul didn't send the service.
     *
     * @param request          service
     * @param responseObserver status
     */
    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        if (log.isDebugEnabled()) {
            log.debug("Received the gRPC server health check with the service name of {}", request.getService());
        }

        HealthCheckResponse.Builder response = HealthCheckResponse.newBuilder();
        response.setStatus(HealthCheckResponse.ServingStatus.SERVING);

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}

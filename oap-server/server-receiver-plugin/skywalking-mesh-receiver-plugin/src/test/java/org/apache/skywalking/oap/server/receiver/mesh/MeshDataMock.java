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

package org.apache.skywalking.oap.server.receiver.mesh;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetricServiceGrpc;

public class MeshDataMock {
    private static boolean IS_COMPLETED = false;

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext().build();

        long startTimestamp = System.currentTimeMillis();
        //long startTimestamp = new DateTime().minusDays(2).getMillis();

        final StreamObserver<ServiceMeshMetric> meshObserver = createMeshObserver(channel);

        for (int i = 0; i < 50; i++) {
            meshObserver.onNext(ServiceMeshMetric.newBuilder()
                                                 .setSourceServiceName("e2e-test-source-service")
                                                 .setSourceServiceInstance("e2e-test-source-service-instance")
                                                 .setDestServiceName("Extra model column are the column defined by in the codes, These columns of model are not required logically in aggregation or further query,")
                                                 .setDestServiceInstance("Extra model column are the column defined by in the codes, These columns of model are not required logically in aggregation or further query,")
                                                 .setEndpoint("Extra model column are the column defined by in the codes, These columns of model are not required logically in aggregation or further query,")
                                                 .setStartTime(System.currentTimeMillis() - 1000L)
                                                 .setEndTime(System.currentTimeMillis() - 500L + i)
                                                 .setLatency(2000)
                                                 .setResponseCode(200)
                                                 .setStatus(true)
                                                 .setProtocol(Protocol.HTTP)
                                                 .setDetectPoint(DetectPoint.server)
                                                 .setInternalErrorCode("rate_limited")
                                                 .build());
        }
        meshObserver.onCompleted();

        while (!IS_COMPLETED) {
            TimeUnit.MILLISECONDS.sleep(500);
        }
    }

    private static StreamObserver<ServiceMeshMetric> createMeshObserver(ManagedChannel channel) {
        ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceStub stub = ServiceMeshMetricServiceGrpc.newStub(
            channel);
        return stub.collect(new StreamObserver<MeshProbeDownstream>() {

            @Override
            public void onNext(final MeshProbeDownstream meshProbeDownstream) {

            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onCompleted() {
                IS_COMPLETED = true;
            }
        });
    }
}

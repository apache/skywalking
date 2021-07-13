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

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.ExportResponse;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;

public class ExporterMockReceiver {
    public static void main(String[] args) throws ServerException, InterruptedException {
        GRPCServer server = new GRPCServer("127.0.0.1", 9870);
        server.initialize();
        server.addHandler(new MockHandler());
        server.start();

        while (true) {
            Thread.sleep(20000L);
        }
    }

    public static class MockHandler extends MetricExportServiceGrpc.MetricExportServiceImplBase implements GRPCHandler {
        @Override
        public StreamObserver<ExportMetricValue> export(StreamObserver<ExportResponse> responseObserver) {
            return new StreamObserver<ExportMetricValue>() {
                @Override
                public void onNext(ExportMetricValue value) {
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void subscription(SubscriptionReq request, StreamObserver<SubscriptionsResp> responseObserver) {
            responseObserver.onNext(SubscriptionsResp.newBuilder()
                                                     .addMetrics(
                                                         SubscriptionMetric
                                                             .newBuilder()
                                                             .setMetricName("all_p99"))
                                                     .addMetrics(
                                                         SubscriptionMetric
                                                             .newBuilder()
                                                             .setMetricName("service_cpm"))
                                                     .addMetrics(
                                                         SubscriptionMetric
                                                             .newBuilder()
                                                             .setMetricName("endpoint_sla"))
                                                     .build());
            responseObserver.onCompleted();
        }
    }
}

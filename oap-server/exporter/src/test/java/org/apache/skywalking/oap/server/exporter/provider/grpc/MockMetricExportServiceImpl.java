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
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.exporter.grpc.EventType;
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.ExportResponse;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;

public class MockMetricExportServiceImpl extends MetricExportServiceGrpc.MetricExportServiceImplBase {
    public final List<ExportMetricValue> exportMetricValues = new ArrayList<>();

    @Override
    public void subscription(SubscriptionReq request, StreamObserver<SubscriptionsResp> responseObserver) {
        SubscriptionsResp resp = SubscriptionsResp.newBuilder()
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("mock-metrics")
                                                          .setEventType(EventType.INCREMENT))
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("int-mock-metrics")
                                                          .setEventType(EventType.INCREMENT))
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("long-mock-metrics")
                                                          .setEventType(EventType.INCREMENT))
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("labeled-mock-metrics")
                                                          .setEventType(EventType.INCREMENT))
                                                  .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ExportMetricValue> export(StreamObserver<ExportResponse> responseObserver) {
        return new StreamObserver<ExportMetricValue>() {
            @Override
            public void onNext(ExportMetricValue value) {
                exportMetricValues.add(value);
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(ExportResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}

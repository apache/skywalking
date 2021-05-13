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
import org.apache.skywalking.oap.server.exporter.grpc.EventType;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;

public class MockMetricExportServiceImpl extends MetricExportServiceGrpc.MetricExportServiceImplBase {
    @Override
    public void subscription(SubscriptionReq request, StreamObserver<SubscriptionsResp> responseObserver) {
        SubscriptionsResp resp = SubscriptionsResp.newBuilder()
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("first")
                                                          .setEventType(EventType.INCREMENT))
                                                  .addMetrics(
                                                      SubscriptionMetric
                                                          .newBuilder()
                                                          .setMetricName("second")
                                                          .setEventType(EventType.INCREMENT))
                                                  .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }
}

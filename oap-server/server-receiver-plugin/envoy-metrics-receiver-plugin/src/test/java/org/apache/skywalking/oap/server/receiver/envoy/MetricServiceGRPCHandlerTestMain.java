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

import com.google.protobuf.TextFormat;
import io.envoyproxy.envoy.service.metrics.v2.MetricsServiceGrpc;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsMessage;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Metrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricServiceGRPCHandlerTestMain {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext().build();

        MetricsServiceGrpc.MetricsServiceStub stub = MetricsServiceGrpc.newStub(channel);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            try {
                send(stub);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 1, TimeUnit.SECONDS);
        Thread.sleep(5000L);
        executor.shutdown();
    }

    private static void send(
        final MetricsServiceGrpc.MetricsServiceStub stub) throws IOException, InterruptedException {
        StreamObserver<StreamMetricsMessage> messageStreamObserver = stub.streamMetrics(new StreamObserver<StreamMetricsResponse>() {
            @Override
            public void onNext(StreamMetricsResponse response) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
        int countdown = 20;
        while (countdown-- > 0) {
            try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-metric.msg"))) {
                StreamMetricsMessage.Builder requestBuilder = StreamMetricsMessage.newBuilder();
                TextFormat.getParser().merge(isr, requestBuilder);

                for (Metrics.MetricFamily.Builder builder : requestBuilder.getEnvoyMetricsBuilderList()) {
                    for (Metrics.Metric.Builder metricBuilder : builder.getMetricBuilderList()) {
                        metricBuilder.setTimestampMs(System.currentTimeMillis());
                    }
                }
                messageStreamObserver.onNext(requestBuilder.build());
                Thread.sleep(200L);
            }
        }
    }

    private static InputStream getResourceAsStream(final String resource) {
        final InputStream in = getContextClassLoader().getResourceAsStream(resource);
        return in == null ? MetricServiceGRPCHandlerTestMain.class.getResourceAsStream(resource) : in;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}

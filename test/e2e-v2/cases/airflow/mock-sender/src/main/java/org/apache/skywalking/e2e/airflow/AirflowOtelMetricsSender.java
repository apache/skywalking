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

package org.apache.skywalking.e2e.airflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Airflow mock e2e OTLP replay sender. Lives under {@code test/e2e-v2/cases/airflow/} so the
 * shared {@code e2e-mock-sender} stays unchanged. Rewrites timestamps and bumps cumulative sum
 * counters so MAL {@code increase('PT1M')} rules observe non-zero values on JSON replay.
 */
@Slf4j
@RestController
@RequestMapping("/otel-metrics")
public class AirflowOtelMetricsSender {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private static final AtomicInteger SEND_SEQ = new AtomicInteger(0);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MetricsServiceGrpc.MetricsServiceStub metricsServiceStub;
    private final String otelMetricsDataPath;

    public AirflowOtelMetricsSender(final AirflowSenderConfiguration configuration) {
        final ManagedChannel channel = NettyChannelBuilder.forAddress(
                                                              configuration.getOapHost(),
                                                              Integer.parseInt(configuration.getOapGrpcPort()))
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();
        this.metricsServiceStub = MetricsServiceGrpc.newStub(channel);
        this.otelMetricsDataPath = configuration.getOtelMetricsDataPath();
    }

    @GetMapping("send")
    public String sendMetricsByTemplate() throws IOException {
        File otelData = new File(this.otelMetricsDataPath);
        if (!otelData.exists() || !otelData.isDirectory()) {
            String msg = "The path must be a folder : " + this.otelMetricsDataPath;
            log.error(msg);
            return msg;
        }
        final File[] files = findJSONFiles(otelData);
        if (files.length == 0) {
            String msg = "The folder doesn't contain any json file : " + this.otelMetricsDataPath;
            log.error(msg);
            return msg;
        }
        for (File file : files) {
            final ExportMetricsServiceRequest.Builder builder = ExportMetricsServiceRequest.newBuilder();
            String jsonData = rewriteTimeField(file);
            JsonFormat.parser().merge(jsonData, builder);
            sendReq(builder);
        }
        return "ok";
    }

    private String rewriteTimeField(File file) throws IOException {
        final int seq = SEND_SEQ.incrementAndGet();
        final long nanoTime = System.currentTimeMillis() * 1000000L;
        final long startNanoTime = nanoTime - 60_000_000_000L;
        final JsonNode root = OBJECT_MAPPER.readTree(file);
        rewriteNode(root, nanoTime, startNanoTime, seq);
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private void rewriteNode(final JsonNode node, final long nanoTime, final long startNanoTime, final int seq) {
        if (node.isObject()) {
            final ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("timeUnixNano")) {
                objectNode.put("timeUnixNano", String.valueOf(nanoTime));
            }
            if (objectNode.has("startTimeUnixNano")) {
                objectNode.put("startTimeUnixNano", String.valueOf(startNanoTime));
            }
            if (objectNode.has("asDouble") && objectNode.has("startTimeUnixNano")) {
                objectNode.put("asDouble", objectNode.get("asDouble").asDouble() + seq);
            }
            final Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                rewriteNode(fields.next().getValue(), nanoTime, startNanoTime, seq);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                rewriteNode(child, nanoTime, startNanoTime, seq);
            }
        }
    }

    private void sendReq(final ExportMetricsServiceRequest.Builder builder) {
        this.metricsServiceStub.export(builder.build(), new StreamObserver<ExportMetricsServiceResponse>() {
            @Override
            public void onNext(final ExportMetricsServiceResponse exportMetricsServiceResponse) {
            }

            @Override
            public void onError(final Throwable throwable) {
                log.error("sendOtelMetrics by template error ", throwable);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    private File[] findJSONFiles(final File otelData) {
        return otelData.listFiles((dir, name) -> name.endsWith(".json"));
    }
}

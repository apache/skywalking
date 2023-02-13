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

package org.apache.skywalking.e2e.controller;

import com.google.protobuf.util.JsonFormat;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.EvaluationListener;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/otel-metrics")
public class OtelMetricsSender {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private final MetricsServiceGrpc.MetricsServiceStub metricsServiceStub;
    private final String otelMetricsDataPath;

    public OtelMetricsSender(final E2EConfiguration configuration) {
        final ManagedChannel channel = NettyChannelBuilder.forAddress(
                                                              configuration.getOapHost(), Integer.parseInt(configuration.getOapGrpcPort()))
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();
        this.metricsServiceStub = MetricsServiceGrpc.newStub(channel);
        this.otelMetricsDataPath = configuration.getOtelMetricsDataPath();
    }

    /**
     * Send otel metrics data that base on JSON formatted file to OAP
     *
     * This method will detect files whose name end with '.json' in the directory specified by
     * `org.apache.skywalking.e2e.E2EConfiguration#otelMetricsDataPath`, and send the data in the file to OAP. Also
     * rewrite field which path is '$..startTimeUnixNano', '$..timeUnixNano' to current time in nano
     */
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
        final long nanoTime = System.nanoTime();
        final Configuration configuration = Configuration.builder()
                                                         .options(Option.SUPPRESS_EXCEPTIONS)
                                                         .evaluationListener(found -> {
                                                             log.info("rewrite json field: {}, {}->{}", found.path(),
                                                                      found.result(), nanoTime
                                                             );
                                                             return EvaluationListener.EvaluationContinuation.CONTINUE;
                                                         }).build();
        final DocumentContext documentContext = JsonPath.using(configuration).parse(file);
        documentContext.set("$..timeUnixNano", nanoTime);
        documentContext.set("$..startTimeUnixNano", nanoTime);
        return documentContext.jsonString();
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

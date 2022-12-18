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

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/aws/otel/eks")
public class AwsEKSOtelMetricsSender {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private final MetricsServiceGrpc.MetricsServiceStub metricsServiceStub;
    private static final String JOB_NAME = "aws-cloud-eks-monitoring";

    public AwsEKSOtelMetricsSender(final E2EConfiguration configuration) {
        final ManagedChannel channel = NettyChannelBuilder.forAddress(
                                                              configuration.getOapHost(), Integer.parseInt(configuration.getOapGrpcPort()))
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();
        this.metricsServiceStub = MetricsServiceGrpc.newStub(channel);
    }

    @GetMapping("send")
    public String sendMetricsByTemplate() {
        final ExportMetricsServiceRequest.Builder builder = ExportMetricsServiceRequest.newBuilder();
        addClusterMetrics(builder.addResourceMetricsBuilder());
        addNodeMemoryMetrics(builder.addResourceMetricsBuilder());
        addPodMemoryMetrics(builder.addResourceMetricsBuilder());

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
        return "ok";
    }

    private void addPodMemoryMetrics(final ResourceMetrics.Builder resourceMetricsBuilder) {
        final Resource.Builder builder = createPodResourceBuilder(System.currentTimeMillis());
        resourceMetricsBuilder.setResource(builder.build());
        final long timeInNano = System.nanoTime();
        resourceMetricsBuilder.addScopeMetricsBuilder().addMetrics(
            Metric.newBuilder()
                  .setName("pod_network_rx_bytes")
                  .setUnit("Count")
                  .setGauge(Gauge.newBuilder().addDataPoints(intDataPointBuilder(timeInNano, 8000L)).build())
                  .build());
    }

    private void addNodeMemoryMetrics(final ResourceMetrics.Builder resourceMetricsBuilder) {
        final Resource.Builder builder = createNodeResourceBuilder(System.currentTimeMillis());
        resourceMetricsBuilder.setResource(builder.build());
        final long timeInNano = System.nanoTime();
        resourceMetricsBuilder.addScopeMetricsBuilder().addMetrics(
            Metric.newBuilder()
                  .setName("node_memory_utilization")
                  .setUnit("Count")
                  .setGauge(Gauge.newBuilder().addDataPoints(intDataPointBuilder(timeInNano, 800L)).build())
                  .build());
    }

    private void addClusterMetrics(ResourceMetrics.Builder resourceMetricsBuilder) {
        final Resource.Builder builder = createClusterResourceBuilder(System.currentTimeMillis());
        resourceMetricsBuilder.setResource(builder.build());

        final long timeInNano = System.nanoTime();

        resourceMetricsBuilder.addScopeMetricsBuilder().addMetrics(
            Metric.newBuilder()
                  .setName("cluster_node_count")
                  .setUnit("Count")
                  .setGauge(Gauge.newBuilder().addDataPoints(intDataPointBuilder(timeInNano, 8L)).build())
                  .build());
    }

    private Resource.Builder createClusterResourceBuilder(Long timestamp) {
        final Resource.Builder builder = Resource.newBuilder();
        builder.addAttributes(keyValue("ClusterName", "SkyWalking"));
        builder.addAttributes(keyValue("NodeName", "ip-172-31-23-33.ap-northeast-1.compute.internal"));
        builder.addAttributes(keyValue("Timestamp", timestamp + ""));
        builder.addAttributes(keyValue("Type", "Cluster"));
        builder.addAttributes(keyValue("Version", "0"));
        builder.addAttributes(keyValue("job_name", JOB_NAME));
        return builder;
    }

    private Resource.Builder createNodeResourceBuilder(Long timestamp) {
        final Resource.Builder builder = Resource.newBuilder();
        builder.addAttributes(keyValue("AutoScalingGroupName", "eks-node-1-52c27f89-29fe-e08d-a931-8270467a8803"));
        builder.addAttributes(keyValue("ClusterName", "SkyWalking"));
        builder.addAttributes(keyValue("InstanceId", "i-0afe883d89558b631"));
        builder.addAttributes(keyValue("InstanceType", "t3.medium"));
        builder.addAttributes(keyValue("NodeName", "ip-172-31-23-33.ap-northeast-1.compute.internal"));
        builder.addAttributes(keyValue("Sources", "[\"cadvisor\",\"pod\",\"calculated\"]"));
        builder.addAttributes(keyValue("Timestamp", timestamp + ""));
        builder.addAttributes(keyValue("Type", "Node"));
        builder.addAttributes(keyValue("Version", "0"));
        builder.addAttributes(keyValue("job_name", JOB_NAME));
        builder.addAttributes(keyValue(
            "kubernetes",
            "{\"host\":\"ip-172-31-23-33.ap-northeast-1.compute.internal\"}"
        ));
        builder.addAttributes(keyValue("pod_status", "Running"));
        return builder;
    }

    private Resource.Builder createPodResourceBuilder(Long timestamp) {
        final Resource.Builder builder = Resource.newBuilder();
        builder.addAttributes(keyValue("AutoScalingGroupName", "eks-node-1-52c27f89-29fe-e08d-a931-8270467a8803"));
        builder.addAttributes(keyValue("ClusterName", "SkyWalking"));
        builder.addAttributes(keyValue("InstanceId", "i-0afe883d89558b631"));
        builder.addAttributes(keyValue("InstanceType", "t3.medium"));
        builder.addAttributes(keyValue("Namespace", "aws-otel-eks"));
        builder.addAttributes(keyValue("NodeName", "ip-172-31-23-33.ap-northeast-1.compute.internal"));
        builder.addAttributes(keyValue("PodName", "aws-otel-eks-ci"));
        builder.addAttributes(keyValue("Sources", "[\"cadvisor\",\"pod\",\"calculated\"]"));
        builder.addAttributes(keyValue("Timestamp", timestamp + ""));
        builder.addAttributes(keyValue("Type", "Pod"));
        builder.addAttributes(keyValue("Version", "0"));
        builder.addAttributes(keyValue("job_name", JOB_NAME));
        builder.addAttributes(keyValue("Service", "kube-dns"));
        builder.addAttributes(keyValue(
            "kubernetes",
            "{\"host\":\"ip-172-31-23-33.ap-northeast-1.compute.internal\",\"labels\":{\"controller-revision-hash\":\"7b5f4f4fff\",\"name\":\"aws-otel-eks-ci\",\"pod-template-generation\":\"1\"},\"namespace_name\":\"aws-otel-eks\",\"pod_id\":\"3a3e479b-986a-40d5-9023-f4c6a259b6ba\",\"pod_name\":\"aws-otel-eks-ci-lzbr8\",\"pod_owners\":[{\"owner_kind\":\"DaemonSet\",\"owner_name\":\"aws-otel-eks-ci\"}]}"
        ));
        builder.addAttributes(keyValue("pod_status", "Running"));
        return builder;
    }

    private NumberDataPoint.Builder intDataPointBuilder(long timeNano, long value) {
        final NumberDataPoint.Builder numberDataPointBuilder = NumberDataPoint.newBuilder();
        numberDataPointBuilder.setTimeUnixNano(System.nanoTime());
        numberDataPointBuilder.setAsInt(value);
        return numberDataPointBuilder;
    }

    private KeyValue keyValue(String k, String v) {
        return KeyValue.newBuilder()
                       .setKey(k)
                       .setValue(AnyValue.newBuilder().setStringValue(v).build())
                       .build();
    }

}

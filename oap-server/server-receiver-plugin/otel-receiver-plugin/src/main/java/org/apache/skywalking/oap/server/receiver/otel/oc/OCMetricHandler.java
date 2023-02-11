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

package org.apache.skywalking.oap.server.receiver.otel.oc;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.opencensus.proto.agent.common.v1.Node;
import io.opencensus.proto.agent.metrics.v1.ExportMetricsServiceRequest;
import io.opencensus.proto.agent.metrics.v1.ExportMetricsServiceResponse;
import io.opencensus.proto.agent.metrics.v1.MetricsServiceGrpc;
import io.opencensus.proto.metrics.v1.DistributionValue;
import io.opencensus.proto.metrics.v1.LabelKey;
import io.opencensus.proto.metrics.v1.LabelValue;
import io.opencensus.proto.metrics.v1.SummaryValue;
import io.opencensus.proto.resource.v1.Resource;
import io.vavr.Function1;
import io.vavr.Tuple;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;
import org.apache.skywalking.oap.server.receiver.otel.Handler;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class OCMetricHandler extends MetricsServiceGrpc.MetricsServiceImplBase implements Handler {
    private static final String HOST_NAME_LABEL = "node_identifier_host_name";
    private List<PrometheusMetricConverter> converters;

    private final ModuleManager manager;

    private final OtelMetricReceiverConfig config;

    @Override public StreamObserver<ExportMetricsServiceRequest> export(
        StreamObserver<ExportMetricsServiceResponse> responseObserver) {
        return new StreamObserver<ExportMetricsServiceRequest>() {
            private Node node;
            private Map<String, String> nodeLabels = new HashMap<>();
            private Resource resource;

            @Override
            public void onNext(ExportMetricsServiceRequest request) {
                if (request.hasNode()) {
                    node = request.getNode();
                    nodeLabels.clear();
                    if (node.hasIdentifier()) {
                        if (StringUtil.isNotBlank(node.getIdentifier().getHostName())) {
                            nodeLabels.put(HOST_NAME_LABEL, node.getIdentifier().getHostName());
                        }
                    }
                    final String name = node.getServiceInfo().getName();
                    if (!Strings.isNullOrEmpty(name)) {
                        nodeLabels.put("job_name", name);
                    }
                }
                //new version of the OTEL moved the host name to the `Resources`
                if (request.hasResource() && StringUtil.isBlank(nodeLabels.get(HOST_NAME_LABEL))) {
                    resource = request.getResource();
                    if (StringUtil.isNotBlank(resource.getLabelsMap().get("net.host.name"))) {
                        nodeLabels.put(HOST_NAME_LABEL, resource.getLabelsMap().get("net.host.name"));
                    }
                }
                converters.forEach(m -> m.toMeter(request.getMetricsList().stream()
                    .flatMap(metric -> metric.getTimeseriesList().stream().map(timeSeries ->
                        Tuple.of(metric.getMetricDescriptor(),
                                 buildLabelsFromNodeInfo(
                                     nodeLabels, buildLabels(
                                         metric.getMetricDescriptor().getLabelKeysList(),
                                         timeSeries.getLabelValuesList()
                                     )
                                 ),
                            timeSeries)))
                    .flatMap(t -> t._3.getPointsList().stream().map(point -> Tuple.of(t._1, t._2, point)))
                    .map(Function1.liftTry(t -> {
                        switch (t._1.getType()) {
                            case GAUGE_INT64:
                                return new Gauge(t._1.getName(), t._2, t._3.getInt64Value(), tsToMilli(t._3.getTimestamp()));
                            case GAUGE_DOUBLE:
                                return new Gauge(t._1.getName(), t._2, t._3.getDoubleValue(), tsToMilli(t._3.getTimestamp()));
                            case CUMULATIVE_INT64:
                                return new Counter(t._1.getName(), t._2, t._3.getInt64Value(), tsToMilli(t._3.getTimestamp()));
                            case CUMULATIVE_DOUBLE:
                                return new Counter(t._1.getName(), t._2, t._3.getDoubleValue(), tsToMilli(t._3.getTimestamp()));
                            case CUMULATIVE_DISTRIBUTION:
                                return new Histogram(t._1.getName(), t._2, t._3.getDistributionValue().getCount(),
                                    t._3.getDistributionValue().getSum(),
                                    buildBuckets(t._3.getDistributionValue()), tsToMilli(t._3.getTimestamp()));
                            case SUMMARY:
                                return new Summary(t._1.getName(), t._2, t._3.getSummaryValue().getCount().getValue(),
                                    t._3.getSummaryValue().getSum().getValue(),
                                    buildQuantiles(t._3.getSummaryValue().getSnapshot()), tsToMilli(t._3.getTimestamp()));
                            default:
                                throw new UnsupportedOperationException("Unsupported OC type:" + t._1.getType());
                        }
                    }))
                    .flatMap(tryIt -> MetricConvert.log(tryIt, "Convert OC metric to prometheus metric"))));
            }

            @Override public void onError(Throwable throwable) {

            }

            @Override public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private static Map<String, String> buildLabels(List<LabelKey> keys, List<LabelValue> values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            result.put(keys.get(i).getKey(), values.get(i).getValue());
        }
        return result;
    }

    private static Map<String, String> buildLabelsFromNodeInfo(Map<String, String> nodeLabels,
                                                               Map<String, String> buildLabelsResult) {
        buildLabelsResult.putAll(nodeLabels);
        return buildLabelsResult;
    }

    private static Map<Double, Long> buildBuckets(DistributionValue distributionValue) {
        Map<Double, Long> result = new HashMap<>();
        List<Double> bounds = distributionValue.getBucketOptions().getExplicit().getBoundsList();
        for (int i = 0; i < bounds.size(); i++) {
            result.put(bounds.get(i), distributionValue.getBuckets(i).getCount());
        }
        result.put(Double.POSITIVE_INFINITY, distributionValue.getBuckets(bounds.size()).getCount());
        return result;
    }

    private static Map<Double, Double> buildQuantiles(SummaryValue.Snapshot snapshot) {
        Map<Double, Double> result = new HashMap<>();
        snapshot.getPercentileValuesList().forEach(p -> result.put(p.getPercentile(), p.getValue()));
        return result;
    }

    private static long tsToMilli(Timestamp timestamp) {
        return timestamp.equals(Timestamp.getDefaultInstance()) ? System.currentTimeMillis() :
            Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    @Override public String type() {
        return "oc";
    }

    @Override
    public void active()
        throws ModuleStartException {
        final List<String> enabledRules =
            Splitter.on(",")
                .omitEmptyStrings()
                .splitToList(config.getEnabledOtelRules());
        final List<Rule> rules;
        try {
            rules = Rules.loadRules("otel-rules", enabledRules);
        } catch (IOException e) {
            throw new ModuleStartException("Failed to load otel rules.", e);
        }
        if (rules.isEmpty()) {
            return;
        }
        GRPCHandlerRegister grpcHandlerRegister = manager.find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        final MeterSystem meterSystem = manager.find(CoreModule.NAME).provider().getService(MeterSystem.class);
        this.converters = rules.stream().map(r -> new PrometheusMetricConverter(r, meterSystem))
            .collect(toList());
        grpcHandlerRegister.addHandler(this);
    }
}

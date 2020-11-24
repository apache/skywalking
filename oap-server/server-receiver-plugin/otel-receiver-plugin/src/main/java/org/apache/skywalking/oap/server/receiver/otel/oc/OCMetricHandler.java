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

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.opencensus.proto.agent.metrics.v1.ExportMetricsServiceRequest;
import io.opencensus.proto.agent.metrics.v1.ExportMetricsServiceResponse;
import io.opencensus.proto.agent.metrics.v1.MetricsServiceGrpc;
import io.opencensus.proto.metrics.v1.DistributionValue;
import io.opencensus.proto.metrics.v1.LabelKey;
import io.opencensus.proto.metrics.v1.LabelValue;
import io.opencensus.proto.metrics.v1.SummaryValue;
import io.vavr.Function1;
import io.vavr.Tuple;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;
import org.apache.skywalking.oap.server.receiver.otel.Handler;

import static java.util.stream.Collectors.toList;

@Slf4j
public class OCMetricHandler extends MetricsServiceGrpc.MetricsServiceImplBase implements Handler {

    private List<PrometheusMetricConverter> metrics;

    @Override public StreamObserver<ExportMetricsServiceRequest> export(
        StreamObserver<ExportMetricsServiceResponse> responseObserver) {
        return new StreamObserver<ExportMetricsServiceRequest>() {
            @Override public void onNext(ExportMetricsServiceRequest request) {
                metrics.forEach(m -> m.toMeter(request.getMetricsList().stream()
                    .flatMap(metric -> metric.getTimeseriesList().stream().map(timeSeries ->
                        Tuple.of(metric.getMetricDescriptor(),
                            buildLabels(metric.getMetricDescriptor().getLabelKeysList(), timeSeries.getLabelValuesList()),
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

    @Override public void active(List<String> enabledRules,
        MeterSystem service, GRPCHandlerRegister grpcHandlerRegister) {
        List<Rule> rules;
        try {
            rules = Rules.loadRules("otel-oc-rules", enabledRules);
        } catch (ModuleStartException e) {
            log.warn("failed to load otel-oc-rules");
            return;
        }
        if (rules.isEmpty()) {
            return;
        }
        this.metrics = rules.stream().map(r ->
            new PrometheusMetricConverter(r, service))
            .collect(toList());
        grpcHandlerRegister.addHandler(this);
    }
}

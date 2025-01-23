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

package org.apache.skywalking.oap.server.baseline;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.Builder;
import lombok.Data;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineMetricsNames;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceGrpc;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineLabeledValue;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineValue;
import org.apache.skywalking.apm.baseline.v3.KeyStringValuePair;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class BaselineQueryServer extends AlarmBaselineServiceGrpc.AlarmBaselineServiceImplBase {

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private static final Map<String, MetricConfig> VALUE_GENERATOR = new HashMap<>();

    static {
        VALUE_GENERATOR.put("service_cpm", MetricConfig.builder()
            .single(true)
            .singleValue(MetricsValueConfig.builder().minValue(0).maxValue(1000).build()).build());
        VALUE_GENERATOR.put("service_sla", MetricConfig.builder()
            .single(true)
            .singleValue(MetricsValueConfig.builder().minValue(8000).maxValue(10000).build()).build());
        VALUE_GENERATOR.put("service_apdex", MetricConfig.builder()
            .single(true)
            .singleValue(MetricsValueConfig.builder().minValue(8000).maxValue(10000).build()).build());
        VALUE_GENERATOR.put("service_resp_time", MetricConfig.builder()
            .single(true)
            .singleValue(MetricsValueConfig.builder().minValue(10).maxValue(1000).build()).build());
        VALUE_GENERATOR.put("service_percentile", MetricConfig.builder()
            .single(false)
            .multiValue(new HashMap<>() {
                {
                    put(KeyStringValuePair.newBuilder().setKey("p").setValue("50").build(),
                        MetricsValueConfig.builder().minValue(0).maxValue(100).build());
                    put(KeyStringValuePair.newBuilder().setKey("p").setValue("75").build(),
                        MetricsValueConfig.builder().minValue(0).maxValue(100).build());
                    put(KeyStringValuePair.newBuilder().setKey("p").setValue("90").build(),
                        MetricsValueConfig.builder().minValue(0).maxValue(100).build());
                    put(KeyStringValuePair.newBuilder().setKey("p").setValue("95").build(),
                        MetricsValueConfig.builder().minValue(0).maxValue(100).build());
                    put(KeyStringValuePair.newBuilder().setKey("p").setValue("99").build(),
                        MetricsValueConfig.builder().minValue(0).maxValue(100).build());
                }
            }).build());
        VALUE_GENERATOR.put("service_status_code", MetricConfig.builder()
            .single(false)
            .multiValue(new HashMap<>() {
                {
                    put(KeyStringValuePair.newBuilder().setKey("status").setValue("200").build(),
                        MetricsValueConfig.builder().minValue(10).maxValue(1000).build());
                    put(KeyStringValuePair.newBuilder().setKey("status").setValue("400").build(),
                        MetricsValueConfig.builder().minValue(10).maxValue(1000).build());
                    put(KeyStringValuePair.newBuilder().setKey("status").setValue("500").build(),
                        MetricsValueConfig.builder().minValue(10).maxValue(1000).build());
                }
            })
            .build());
    }

    @Override
    public void querySupportedMetricsNames(Empty request, StreamObserver<AlarmBaselineMetricsNames> responseObserver) {
        responseObserver.onNext(AlarmBaselineMetricsNames.newBuilder()
            .addAllMetricNames(VALUE_GENERATOR.keySet())
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void queryPredictedMetrics(org.apache.skywalking.apm.baseline.v3.AlarmBaselineRequest request, StreamObserver<org.apache.skywalking.apm.baseline.v3.AlarmBaselineResponse> responseObserver) {
        final List<org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceMetric> metrics = new ArrayList<>();
        final List<Long> timeBucketRange = generateTimeBucketRange(request);

        for (org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceMetricName serviceMetricName : request.getServiceMetricNamesList()) {
            final List<org.apache.skywalking.apm.baseline.v3.AlarmBaselineMetricPrediction> predictions = new ArrayList<>();

            for (String metricName : serviceMetricName.getMetricNamesList()) {
                final List<org.apache.skywalking.apm.baseline.v3.AlarmBaselinePredicatedValue> values = timeBucketRange.stream()
                    .map(t -> VALUE_GENERATOR.get(metricName).generate(t))
                    .collect(Collectors.toList());

                predictions.add(org.apache.skywalking.apm.baseline.v3.AlarmBaselineMetricPrediction.newBuilder()
                    .setName(metricName)
                    .addAllValues(values)
                    .build());
            }

            metrics.add(org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceMetric.newBuilder()
                .setServiceName(serviceMetricName.getServiceName())
                .addAllPredictions(predictions)
                .build());
        }

        responseObserver.onNext(org.apache.skywalking.apm.baseline.v3.AlarmBaselineResponse.newBuilder().addAllServiceMetrics(metrics).build());
        responseObserver.onCompleted();
    }

    private List<Long> generateTimeBucketRange(org.apache.skywalking.apm.baseline.v3.AlarmBaselineRequest request) {
        if (request.getStep() != org.apache.skywalking.apm.baseline.v3.TimeBucketStep.HOUR) {
            return Collections.emptyList();
        }

        final Calendar start = Calendar.getInstance();
        start.setTimeInMillis(TimeBucket.getTimestamp(request.getStartTimeBucket(), DownSampling.Hour));

        final Calendar end = Calendar.getInstance();
        end.setTimeInMillis(TimeBucket.getTimestamp(request.getEndTimeBucket(), DownSampling.Hour));

        final ArrayList<Long> result = new ArrayList<>();
        while (start.getTimeInMillis() <= end.getTimeInMillis()) {
            result.add(TimeBucket.getTimeBucket(start.getTimeInMillis(), DownSampling.Hour));
            start.add(Calendar.HOUR, 1);
        }
        return result;
    }

    @Data
    @Builder
    private static class MetricConfig {
        private boolean single;
        private MetricsValueConfig singleValue;
        private Map<KeyStringValuePair, MetricsValueConfig> multiValue;

        public org.apache.skywalking.apm.baseline.v3.AlarmBaselinePredicatedValue generate(long timeBucket) {
            if (single) {
                return org.apache.skywalking.apm.baseline.v3.AlarmBaselinePredicatedValue.newBuilder()
                    .setTimeBucket(timeBucket)
                    .setSingleValue(org.apache.skywalking.apm.baseline.v3.AlarmBaselineSingleValue.newBuilder().setValue(singleValue.generateValue()))
                    .build();
            }

            final org.apache.skywalking.apm.baseline.v3.AlarmBaselinePredicatedValue.Builder builder = org.apache.skywalking.apm.baseline.v3.AlarmBaselinePredicatedValue.newBuilder();
            builder.setTimeBucket(timeBucket);
            builder.setLabeledValue(AlarmBaselineLabeledValue.newBuilder()
                .addAllValues(multiValue.entrySet().stream()
                    .map(e -> AlarmBaselineLabeledValue.LabelWithValue.newBuilder()
                        .addLabels(e.getKey())
                        .setValue(e.getValue().generateValue())
                        .build())
                    .collect(Collectors.toList()))
                .build());
            return builder.build();
        }
    }

    @Data
    @Builder
    private static class MetricsValueConfig {
        private long minValue;
        private long maxValue;

        public AlarmBaselineValue generateValue() {
            final AlarmBaselineValue.Builder valueBuilder = AlarmBaselineValue.newBuilder();
            valueBuilder.setLowerValue(generateRandomValue(minValue, maxValue));
            valueBuilder.setUpperValue(generateRandomValue(valueBuilder.getLowerValue(), maxValue));
            valueBuilder.setValue(generateRandomValue(valueBuilder.getLowerValue(), valueBuilder.getUpperValue()));
            return valueBuilder.build();
        }
    }

    private static long generateRandomValue(long minValue, long maxValue) {
        int range = Math.toIntExact(maxValue - minValue);
        return minValue + RANDOM.nextInt(range);
    }
}

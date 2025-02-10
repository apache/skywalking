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

package org.apache.skywalking.oap.server.baseline.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineMetricPrediction;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineMetricsNames;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineRequest;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineResponse;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceGrpc;
import org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceMetricName;
import org.apache.skywalking.apm.baseline.v3.KeyStringValuePair;
import org.apache.skywalking.apm.baseline.v3.TimeBucketStep;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BaselineQueryServiceImpl implements BaselineQueryService {
    private AlarmBaselineServiceGrpc.AlarmBaselineServiceBlockingStub stub;
    private final Cache<String/*timeBucket,serviceName*/, Map<String/*metricName*/, PredictServiceMetrics.PredictMetricsValue>> baselineCache;

    public BaselineQueryServiceImpl(String addr, int port) {
        this.baselineCache = CacheBuilder.newBuilder()
                                         .expireAfterAccess(1, TimeUnit.HOURS)
                                         .build();
        if (StringUtil.isEmpty(addr) || port <= 0) {
            return;
        }
        GRPCClient client = new GRPCClient(addr, port);
        client.connect();
        ManagedChannel channel = client.getChannel();
        stub = AlarmBaselineServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public List<String> querySupportedMetrics() {
        if (stub == null) {
            log.warn("Baseline service is not set up, return empty list.");
            return Collections.emptyList();
        }

        final AlarmBaselineMetricsNames names = stub.querySupportedMetricsNames(Empty.newBuilder().build());
        return Optional.ofNullable(names)
            .map(AlarmBaselineMetricsNames::getMetricNamesList)
            .map(ArrayList::new).orElse(new ArrayList<>(0));
    }

    public List<PredictServiceMetrics> queryPredictMetrics(List<ServiceMetrics> serviceMetrics, long startTimeBucket, long endTimeBucket) {
        if (stub == null) {
            log.warn("Baseline service is not set up, return empty baseline values.");
            return Collections.emptyList();
        }

        try {
            return queryPredictMetrics0(serviceMetrics, startTimeBucket, endTimeBucket);
        } catch (Exception e) {
            log.warn("Query baseline failure", e);
        }
        return Collections.emptyList();
    }

    public Map<String, PredictServiceMetrics.PredictMetricsValue> queryPredictMetricsFromCache(String serviceName,
                                                                                               String timeBucketHour) {
        if (stub == null) {
            log.warn("Baseline service is not set up, return empty baseline values.");
            return Collections.emptyMap();
        }
        String key = timeBucketHour + Const.COMMA + serviceName;
        Map<String, PredictServiceMetrics.PredictMetricsValue> baselineValues = this.baselineCache.asMap().get(key);

        if (CollectionUtils.isNotEmpty(baselineValues)) {
            return baselineValues;
        }
        //reload all metrics and timeBucket baseline values for this service
        List<String> metrics = querySupportedMetrics();
        ServiceMetrics serviceMetrics = ServiceMetrics.builder()
                                                      .serviceName(serviceName)
                                                      .metricsNames(metrics)
                                                      .build();
        //todo: need config?
        long startTimeBucket = TimeBucket.getTimeBucket(
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24), DownSampling.Hour);
        long endTimeBucket = TimeBucket.getTimeBucket(
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24), DownSampling.Hour);
        List<PredictServiceMetrics> predictServiceMetricsList = queryPredictMetrics(
            Collections.singletonList(serviceMetrics), startTimeBucket, endTimeBucket);
        if (CollectionUtils.isEmpty(predictServiceMetricsList)) {
            return Collections.emptyMap();
        }
        for (String metricName : metrics) {
            for (PredictServiceMetrics predictServiceMetrics : predictServiceMetricsList) {
                List<PredictServiceMetrics.PredictMetricsValue> predictMetricsValues = predictServiceMetrics.getMetricsValues()
                                                                                                            .get(
                                                                                                                metricName);
                if (CollectionUtils.isEmpty(predictMetricsValues)) {
                    continue;
                }
                for (PredictServiceMetrics.PredictMetricsValue predictMetricsValue : predictMetricsValues) {
                    if (predictMetricsValue == null) {
                        continue;
                    }
                    this.baselineCache.asMap()
                                      .computeIfAbsent(
                                          predictMetricsValue.getTimeBucket() + Const.COMMA + serviceName,
                                          k -> new HashMap<>()
                                      )
                                      .put(metricName, predictMetricsValue);
                }
            }
        }
        return this.baselineCache.asMap().getOrDefault(key, Collections.emptyMap());
    }

    private List<PredictServiceMetrics> queryPredictMetrics0(List<ServiceMetrics> serviceMetrics, long startTimeBucket, long endTimeBucket) {
        // building request
        final AlarmBaselineRequest.Builder request = AlarmBaselineRequest.newBuilder();
        serviceMetrics.forEach(s -> {
            final AlarmBaselineServiceMetricName.Builder serviceMetricsBuilder = AlarmBaselineServiceMetricName.newBuilder();
            serviceMetricsBuilder.setServiceName(s.getServiceName());
            serviceMetricsBuilder.addAllMetricNames(s.getMetricsNames());
            request.addServiceMetricNames(serviceMetricsBuilder);
        });
        request.setStartTimeBucket(startTimeBucket);
        request.setEndTimeBucket(endTimeBucket);
        // Only support hour level for now
        request.setStep(TimeBucketStep.HOUR);

        // send request and convert response
        final AlarmBaselineResponse response = stub.queryPredictedMetrics(request.build());
        return response.getServiceMetricsList().stream().map(s -> {
            final PredictServiceMetrics.PredictServiceMetricsBuilder builder = PredictServiceMetrics.builder();
            builder.serviceName(s.getServiceName());
            builder.metricsValues(s.getPredictionsList().stream().collect(
                Collectors.toMap(AlarmBaselineMetricPrediction::getName, p -> p.getValuesList().stream().map(v -> {
                    final PredictServiceMetrics.PredictMetricsValue.PredictMetricsValueBuilder valueBuilder =
                        PredictServiceMetrics.PredictMetricsValue.builder();
                    valueBuilder.timeBucket(v.getTimeBucket());
                    // parsing single value
                    if (v.hasSingleValue()) {
                        valueBuilder.singleValue(PredictServiceMetrics.PredictSingleValue.builder()
                            .value(v.getSingleValue().getValue().getValue())
                            .upperValue(v.getSingleValue().getValue().getUpperValue())
                            .lowerValue(v.getSingleValue().getValue().getLowerValue())
                            .build());
                    }
                    // parsing labeled values
                    if (v.hasLabeledValue()) {
                        valueBuilder.labeledValue(v.getLabeledValue().getValuesList().stream().map(l -> {
                            final PredictServiceMetrics.PredictLabelValue.PredictLabelValueBuilder labelBuilder =
                                PredictServiceMetrics.PredictLabelValue.builder();
                            labelBuilder.labels(l.getLabelsList().stream().collect(
                                Collectors.toMap(KeyStringValuePair::getKey, KeyStringValuePair::getValue)));
                            labelBuilder.value(PredictServiceMetrics.PredictSingleValue.builder()
                                .value(l.getValue().getValue())
                                .upperValue(l.getValue().getUpperValue())
                                .lowerValue(l.getValue().getLowerValue())
                                .build());
                            return labelBuilder.build();
                        }).collect(Collectors.toList()));
                    }
                    return valueBuilder.build();
                }).collect(Collectors.toList()))));
            return builder.build();
        }).collect(Collectors.toList());
    }
}

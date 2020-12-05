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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.HistogramType;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

/**
 * Process meter when receive the meter data.
 */
@Slf4j
public class MeterProcessor {

    /**
     * Process context.
     */
    private final MeterProcessService processService;

    /**
     * All of meters has been read. Using it to process groovy script.
     */
    private final Map<String, List<SampleBuilder>> meters = new HashMap<>();

    /**
     * Agent service name.
     */
    private String service;

    /**
     * Agent service instance name.
     */
    private String serviceInstance;

    /**
     * Agent send time.
     */
    private Long timestamp;

    public MeterProcessor(MeterProcessService processService) {
        this.processService = processService;
    }

    public void read(MeterData data) {
        // Parse and save meter
        switch (data.getMetricCase()) {
            case SINGLEVALUE:
                MeterSingleValue single = data.getSingleValue();
                meters.computeIfAbsent(single.getName(), k -> new ArrayList<>()).add(SampleBuilder.builder()
                    .name(single.getName())
                    .labels(single.getLabelsList().stream().collect(toImmutableMap(Label::getName, Label::getValue)))
                    .value(single.getValue())
                    .build());
                break;
            case HISTOGRAM:
                MeterHistogram histogram = data.getHistogram();
                Map<String, String> baseLabel = histogram.getLabelsList().stream().collect(Collectors.toMap(Label::getName, Label::getValue));
                meters.computeIfAbsent(histogram.getName(), k -> new ArrayList<>())
                    .addAll(histogram.getValuesList().stream().map(v ->
                        SampleBuilder.builder()
                            .name(histogram.getName())
                            .labels(ImmutableMap.<String, String>builder()
                                .putAll(baseLabel)
                                .put("le", String.valueOf(v.getBucket())).build())
                            .value(v.getCount()).build()
                ).collect(Collectors.toList()));
                break;
            default:
                return;
        }

        // Agent info
        if (StringUtil.isNotEmpty(data.getService())) {
            service = data.getService();
        }
        if (StringUtil.isNotEmpty(data.getServiceInstance())) {
            serviceInstance = data.getServiceInstance();
        }
        if (data.getTimestamp() > 0) {
            timestamp = data.getTimestamp();
        }
    }

    /**
     * Process all of meters and send to meter system.
     */
    public void process() {
        // Check agent information
        if (StringUtils.isEmpty(service) || StringUtil.isEmpty(serviceInstance) || timestamp == null) {
            return;
        }

        // Get all meter builders.
        final List<MetricConvert> converts = processService.converts();
        if (CollectionUtils.isEmpty(converts)) {
            return;
        }

        try {
            converts.stream().forEach(convert -> convert.toMeter(meters.entrySet().stream().collect(toImmutableMap(
                Map.Entry::getKey,
                v -> SampleFamilyBuilder.newBuilder(
                    v.getValue().stream().map(s -> s.build(service, serviceInstance, timestamp)).toArray(Sample[]::new)
                ).histogramType(HistogramType.ORDINARY).defaultHistogramBucketUnit(TimeUnit.MILLISECONDS).build()
            ))));
        } catch (Exception e) {
            log.warn("Process meters failure.", e);
        }
    }

}

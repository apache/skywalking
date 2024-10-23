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

package org.apache.skywalking.oap.server.receiver.telegraf.provider.handler;

import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.RequestConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafData;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafDatum;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TelegrafServiceHandler {

    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;
    private List<MetricConvert> metricConvert;

    public TelegrafServiceHandler(ModuleManager moduleManager, MeterSystem meterSystem, List<Rule> rules) {

        this.metricConvert = rules.stream().map(r -> new MetricConvert(r, meterSystem)).collect(Collectors.toList());

        final MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                           .provider()
                                                           .getService(MetricsCreator.class);

        histogram = metricsCreator.createHistogramMetric(
                "telegraf_in_latency_seconds", "The process latency of telegraf data",
                new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );

        errorCounter = metricsCreator.createCounter(
                "telegraf_error_count", "The error number of telegraf analysis",
                new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    /**
    * Convert the TelegrafData object to meter {@link Sample}
    **/
    public List<Sample> convertTelegraf(TelegrafDatum telegrafData) {

        List<Sample> sampleList = new ArrayList<>();

        Map<String, Object> fields = telegrafData.getFields();
        String name = telegrafData.getName();
        Map<String, String> tags = telegrafData.getTags();
        ImmutableMap<String, String> immutableTags = ImmutableMap.copyOf(tags);
        long timestamp = telegrafData.getTimestamp();

        fields.forEach((key, value) -> {
            if (value instanceof Number) {
                Sample.SampleBuilder builder = Sample.builder();
                Sample sample = builder.name(name + "_" + key)
                        .timestamp(timestamp * 1000L)
                        .value(((Number) value).doubleValue())
                        .labels(immutableTags).build();

                sampleList.add(sample);
            }
        });
        return sampleList;

    }

    public List<ImmutableMap<String, SampleFamily>> convertSampleFamily(TelegrafData telegrafData) {
        List<Sample> allSamples = new ArrayList<>();

        List<TelegrafDatum> metrics = telegrafData.getMetrics();
        for (TelegrafDatum m : metrics) {
            List<Sample> samples = convertTelegraf(m);
            allSamples.addAll(samples);
        }

        List<ImmutableMap<String, SampleFamily>> res = new ArrayList<>();

        // Grouping all samples by timestamp name
        Map<Long, List<Sample>> sampleFamilyByTime = allSamples.stream()
                .collect(Collectors.groupingBy(Sample::getTimestamp));

        // Grouping all samples with the same timestamp by name
        for (List<Sample> s : sampleFamilyByTime.values()) {
            ImmutableMap.Builder<String, SampleFamily> builder = ImmutableMap.builder();
            Map<String, List<Sample>> sampleFamilyByName = s.stream()
                    .collect(Collectors.groupingBy(Sample::getName));
            sampleFamilyByName.forEach((k, v) ->
                    builder.put(k, SampleFamilyBuilder.newBuilder(v.toArray(new Sample[0])).build()));
            res.add(builder.build());
        }

        return res;
    }

    @Post("/telegraf")
    @RequestConverter(TelegrafData.class)
    public Commands collectData(TelegrafData telegrafData) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            List<ImmutableMap<String, SampleFamily>> sampleFamily = convertSampleFamily(telegrafData);
            sampleFamily.forEach(s -> metricConvert.forEach(m -> m.toMeter(s)));
        } catch (Exception e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        }
        return Commands.newBuilder().build();
    }
}

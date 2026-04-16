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
 */

package org.apache.skywalking.oap.analyzer.ios.listener;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.trace.SpanListener;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Extracts Apple MetricKit daily statistics from OTLP spans and converts them
 * to MAL metrics. MetricKit spans are NOT stored as traces (24-hour duration).
 *
 * <p>Detection: {@code scopeName == "MetricKit"} AND {@code spanName == "MXMetricPayload"}.
 *
 * <p>The listener converts span attributes into labeled {@link SampleFamily} samples
 * and feeds them into the MAL pipeline via {@link MetricConvert}.
 */
@Slf4j
public class IOSMetricKitSpanListener implements SpanListener {
    private List<MetricConvert> converters;

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    @Override
    public void init(final ModuleManager moduleManager) {
        final MeterSystem meterSystem = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(MeterSystem.class);
        try {
            final List<Rule> rules = Rules.loadRules(
                "otel-rules", java.util.Collections.singletonList("ios"));
            converters = rules.stream()
                              .map(r -> new MetricConvert(r, meterSystem))
                              .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            log.warn("Failed to load iOS MAL rules from otel-rules/ios/, "
                + "MetricKit metrics will not be processed", e);
            converters = java.util.Collections.emptyList();
        }
        log.info("IOSMetricKitSpanListener initialized with {} MAL converters", converters.size());
    }

    @Override
    public SpanListenerResult onOTLPSpan(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes,
                                         final String scopeName,
                                         final String scopeVersion) {
        if (!"MetricKit".equals(scopeName) || !"MXMetricPayload".equals(span.spanName())) {
            return SpanListenerResult.CONTINUE;
        }

        if (converters.isEmpty()) {
            log.debug("No MAL converters for iOS MetricKit, skipping");
            return SpanListenerResult.builder().shouldPersist(false).build();
        }

        final String serviceName = resourceAttributes.getOrDefault("service.name", "");
        final String serviceVersion = resourceAttributes.getOrDefault("service.version", "");
        final String deviceModel = getDeviceModel(span, resourceAttributes);
        final String osVersion = getOsVersion(span, resourceAttributes);
        final long timestamp = span.endTimeNanos() / 1_000_000;

        final ImmutableMap<String, String> labels = ImmutableMap.of(
            "service_name", serviceName,
            "service_instance_id", serviceVersion,
            "device_model", deviceModel,
            "os_version", osVersion
        );

        // Build sample families from span attributes
        final Map<String, SampleFamily> sampleFamilies = new HashMap<>();
        addSample(sampleFamilies, "metrickit_app_launch_time", span, "metrickit.app_launch.time_to_first_draw_average", labels, timestamp, 1000.0);
        addSample(sampleFamilies, "metrickit_hang_time", span, "metrickit.app_responsiveness.hang_time_average", labels, timestamp, 1000.0);
        addSample(sampleFamilies, "metrickit_cpu_time", span, "metrickit.cpu.cpu_time", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_gpu_time", span, "metrickit.gpu.time", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_peak_memory", span, "metrickit.memory.peak_memory_usage", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_disk_write", span, "metrickit.diskio.logical_write_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_wifi_download", span, "metrickit.network_transfer.wifi_download", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_wifi_upload", span, "metrickit.network_transfer.wifi_upload", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_cellular_download", span, "metrickit.network_transfer.cellular_download", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_cellular_upload", span, "metrickit.network_transfer.cellular_upload", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_abnormal_exit_count", span, "metrickit.app_exit.foreground.abnormal_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_normal_exit_count", span, "metrickit.app_exit.foreground.normal_app_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_oom_kill_count", span, "metrickit.app_exit.background.memory_pressure_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_scroll_hitch_ratio", span, "metrickit.animation.scroll_hitch_time_ratio", labels, timestamp, 1.0);

        if (!sampleFamilies.isEmpty()) {
            final ImmutableMap<String, SampleFamily> immutableSamples = ImmutableMap.copyOf(sampleFamilies);
            converters.forEach(convert -> convert.toMeter(immutableSamples));
        }

        // Do NOT persist as trace — 24-hour span is not meaningful in trace view
        return SpanListenerResult.builder().shouldPersist(false).build();
    }

    private void addSample(final Map<String, SampleFamily> families,
                           final String metricName,
                           final OTLPSpanReader span,
                           final String attrKey,
                           final ImmutableMap<String, String> labels,
                           final long timestamp,
                           final double multiplier) {
        final String rawValue = span.getAttribute(attrKey);
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        try {
            final double value = Double.parseDouble(rawValue) * multiplier;
            final Sample sample = Sample.builder()
                                        .name(metricName)
                                        .labels(labels)
                                        .value(value)
                                        .timestamp(timestamp)
                                        .build();
            families.put(metricName, SampleFamilyBuilder.newBuilder(sample).build());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse MetricKit attribute {}: {}", attrKey, rawValue);
        }
    }

    private static String getDeviceModel(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes) {
        // Prefer span-level metadata (from MetricKit payload) over resource attribute
        final String fromSpan = span.getAttribute("metrickit.metadata.device_type");
        if (fromSpan != null && !fromSpan.isEmpty()) {
            return fromSpan;
        }
        return resourceAttributes.getOrDefault("device.model.identifier", "unknown");
    }

    private static String getOsVersion(final OTLPSpanReader span,
                                       final Map<String, String> resourceAttributes) {
        final String fromSpan = span.getAttribute("metrickit.metadata.os_version");
        if (fromSpan != null && !fromSpan.isEmpty()) {
            return fromSpan;
        }
        return resourceAttributes.getOrDefault("os.version", "unknown");
    }
}

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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.trace.SpanListener;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverModule;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;

/**
 * Extracts Apple MetricKit daily statistics from OTLP spans and converts them
 * to MAL metrics. MetricKit spans are NOT stored as traces (24-hour duration).
 *
 * <p>Detection: {@code scopeName == "MetricKit"} AND {@code spanName == "MXMetricPayload"}.
 * These values come from the current OpenTelemetry Swift MetricKit
 * instrumentation, which builds the tracer/logger with scope name
 * {@code "MetricKit"} and reports the main aggregated metrics span as
 * {@code "MXMetricPayload"}.
 *
 * <p>The listener converts span attributes into labeled {@link SampleFamily} samples
 * and pushes them into the shared MAL pipeline via
 * {@link OpenTelemetryMetricRequestProcessor#toMeter}.
 * MAL rules are loaded by the otel-receiver module via {@code enabledOtelMetricsRules}.
 */
@Slf4j
public class IOSMetricKitSpanListener implements SpanListener {
    /**
     * Histogram bucket boundaries for app launch time (ms).
     * Covers common ranges from fast launch to slow cold start. The last bucket (30 s)
     * acts as a finite overflow sentinel: observations above it are catastrophic and
     * should remain visible on the chart rather than landing in a {@code +Inf} bucket
     * (which would surface as {@code Long.MAX_VALUE} in percentile queries).
     */
    private static final double[] APP_LAUNCH_BUCKETS =
        {400, 600, 800, 1000, 1500, 2000, 3000, 5000, 10000, 30000};

    /**
     * Histogram bucket boundaries for hang time (ms).
     * Covers common ranges from minor UI jank to severe hangs. MetricKit hard-caps
     * hang observation near 30 s; that value is used as the finite overflow sentinel
     * (see {@link #APP_LAUNCH_BUCKETS} for rationale).
     */
    private static final double[] HANG_TIME_BUCKETS =
        {250, 400, 600, 1000, 1500, 2000, 3000, 5000, 10000, 30000};

    private OpenTelemetryMetricRequestProcessor metricProcessor;

    @Override
    public String[] requiredModules() {
        return new String[] {OtelMetricReceiverModule.NAME};
    }

    @Override
    public void init(final ModuleManager moduleManager) {
        metricProcessor = moduleManager.find(OtelMetricReceiverModule.NAME)
                                       .provider()
                                       .getService(OpenTelemetryMetricRequestProcessor.class);
    }

    @Override
    public SpanListenerResult onOTLPSpan(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes,
                                         final String scopeName,
                                         final String scopeVersion) {
        final String osName = resourceAttributes.get("os.name");
        if (!"iOS".equals(osName) && !"iPadOS".equals(osName)) {
            return SpanListenerResult.CONTINUE;
        }
        // OpenTelemetry Swift MetricKit instrumentation emits the aggregated
        // daily metrics span under scope "MetricKit" with span name
        // "MXMetricPayload".
        if (!"MetricKit".equals(scopeName) || !"MXMetricPayload".equals(span.spanName())) {
            return SpanListenerResult.CONTINUE;
        }

        final String serviceName = resourceAttributes.getOrDefault("service.name", "");
        final String serviceVersion = resourceAttributes.getOrDefault("service.version", "");
        final String deviceModel = getDeviceModel(span, resourceAttributes);
        final String osVersion = getOsVersion(span, resourceAttributes);
        final long timestamp = span.endTimeNanos() > 0
            ? span.endTimeNanos() / 1_000_000 : System.currentTimeMillis();

        final ImmutableMap<String, String> labels = ImmutableMap.of(
            "service_name", serviceName,
            "service_instance_id", serviceVersion,
            "device_model", deviceModel,
            "os_version", osVersion
        );

        // Build sample families from span attributes
        final Map<String, SampleFamily> sampleFamilies = new HashMap<>();
        addHistogramSamples(sampleFamilies, "metrickit_app_launch_time_histogram", span, "metrickit.app_launch.time_to_first_draw_average", labels, timestamp, 1000.0, APP_LAUNCH_BUCKETS);
        addSample(sampleFamilies, "metrickit_hang_time", span, "metrickit.app_responsiveness.hang_time_average", labels, timestamp, 1000.0);
        addHistogramSamples(sampleFamilies, "metrickit_hang_time_histogram", span, "metrickit.app_responsiveness.hang_time_average", labels, timestamp, 1000.0, HANG_TIME_BUCKETS);
        addSample(sampleFamilies, "metrickit_cpu_time", span, "metrickit.cpu.cpu_time", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_gpu_time", span, "metrickit.gpu.time", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_peak_memory", span, "metrickit.memory.peak_memory_usage", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_disk_write", span, "metrickit.diskio.logical_write_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_wifi_download", span, "metrickit.network_transfer.wifi_download", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_wifi_upload", span, "metrickit.network_transfer.wifi_upload", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_cellular_download", span, "metrickit.network_transfer.cellular_download", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_cellular_upload", span, "metrickit.network_transfer.cellular_upload", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_foreground_abnormal_exit_count", span, "metrickit.app_exit.foreground.abnormal_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_background_abnormal_exit_count", span, "metrickit.app_exit.background.abnormal_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_background_oom_kill_count", span, "metrickit.app_exit.background.memory_pressure_exit_count", labels, timestamp, 1.0);
        addSample(sampleFamilies, "metrickit_scroll_hitch_ratio", span, "metrickit.animation.scroll_hitch_time_ratio", labels, timestamp, 1.0);

        if (!sampleFamilies.isEmpty()) {
            metricProcessor.toMeter(ImmutableMap.copyOf(sampleFamilies));
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

    private void addHistogramSamples(final Map<String, SampleFamily> families,
                                      final String metricName,
                                      final OTLPSpanReader span,
                                      final String attrKey,
                                      final ImmutableMap<String, String> labels,
                                      final long timestamp,
                                      final double multiplier,
                                      final double[] buckets) {
        final String rawValue = span.getAttribute(attrKey);
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        try {
            final double value = Double.parseDouble(rawValue) * multiplier;
            // Per-bucket counts (non-cumulative): place the observation into the smallest
            // bucket whose upper bound is >= value, matching the OTLP histogram convention
            // that MAL's histogram_percentile expects. Values exceeding the largest bucket
            // fall into the final (30 s) overflow sentinel.
            int targetBucket = buckets.length - 1;
            for (int i = 0; i < buckets.length; i++) {
                if (value <= buckets[i]) {
                    targetBucket = i;
                    break;
                }
            }
            final Sample[] samples = new Sample[buckets.length];
            for (int i = 0; i < buckets.length; i++) {
                final ImmutableMap<String, String> bucketLabels = ImmutableMap.<String, String>builder()
                    .putAll(labels)
                    .put("le", String.valueOf((long) buckets[i]))
                    .build();
                samples[i] = Sample.builder()
                                   .name(metricName)
                                   .labels(bucketLabels)
                                   .value(i == targetBucket ? 1 : 0)
                                   .timestamp(timestamp)
                                   .build();
            }
            // Bucket `le` labels are already in milliseconds; disable MAL's default
            // SECONDS→MS rescale (which would otherwise multiply them by 1000).
            families.put(
                metricName,
                SampleFamilyBuilder.newBuilder(samples)
                                   .defaultHistogramBucketUnit(TimeUnit.MILLISECONDS)
                                   .build());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse MetricKit attribute {}: {}", attrKey, rawValue);
        }
    }

    private static String getDeviceModel(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes) {
        // OpenTelemetry Swift MetricKit instrumentation puts device metadata on
        // the payload span as `metrickit.metadata.*` attributes. Prefer those
        // over the generic resource attributes when available.
        final String fromSpan = span.getAttribute("metrickit.metadata.device_type");
        if (fromSpan != null && !fromSpan.isEmpty()) {
            return fromSpan;
        }
        return resourceAttributes.getOrDefault("device.model.identifier", "unknown");
    }

    private static String getOsVersion(final OTLPSpanReader span,
                                       final Map<String, String> resourceAttributes) {
        // Same as device type above: prefer the MetricKit payload metadata
        // attribute over the generic resource-level OS version.
        final String fromSpan = span.getAttribute("metrickit.metadata.os_version");
        if (fromSpan != null && !fromSpan.isEmpty()) {
            return fromSpan;
        }
        return resourceAttributes.getOrDefault("os.version", "unknown");
    }
}

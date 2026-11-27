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

package org.apache.skywalking.oap.meter.analyzer.v2;

import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.google.common.collect.ImmutableMap.of;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage for Analyzer building histogram / percentile meters when a bucket
 * is labeled {@code le="+Inf"} (Prometheus) or {@code Infinity} (Java). FunctionTest only
 * asserts the MAL expression result; this hits {@link Analyzer#analyse} which parses {@code le}
 * again while assembling {@link BucketedValues}.
 */
@ExtendWith(MockitoExtension.class)
public class AnalyzerHistogramInfTest {

    @Mock
    private MeterSystem meterSystem;

    @BeforeAll
    public static void setUp() {
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    void analysePercentileAcceptsPrometheusPlusInfBucket() {
        final AcceptableValue<PercentileArgument> sink = mock(AcceptableValue.class);
        when(meterSystem.buildMetrics(anyString(), eq(PercentileArgument.class))).thenReturn(sink);
        doNothing().when(meterSystem).create(anyString(), anyString(), any());
        doNothing().when(meterSystem).doStreamingCalculation(any());

        final Analyzer analyzer = Analyzer.build(
            "http_latency_percentile",
            null,
            "http_request_duration_bucket.histogram().histogram_percentile([75, 99])"
                + ".service(['service'], Layer.GENERAL)",
            meterSystem
        );

        final ImmutableMap<String, SampleFamily> input = of(
            "http_request_duration_bucket",
            SampleFamilyBuilder.newBuilder(
                sample("0.025", 100),
                sample("0.75", 22),
                sample("1.25", 30),
                sample("+Inf", 40)
            ).build()
        );

        try (MockedStatic<MetricsStreamProcessor> ignored = mockStatic(MetricsStreamProcessor.class)) {
            final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
            ignored.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
            assertDoesNotThrow(() -> analyzer.analyse(input));
        }

        final ArgumentCaptor<PercentileArgument> captor = ArgumentCaptor.forClass(PercentileArgument.class);
        verify(sink).accept(any(MeterEntity.class), captor.capture());
        final BucketedValues buckets = captor.getValue().getBucketedValues();
        assertArrayEquals(
            new long[] {25L, 750L, 1250L, Long.MAX_VALUE},
            buckets.getBuckets()
        );
        assertArrayEquals(new long[] {100L, 22L, 30L, 40L}, buckets.getValues());
        assertArrayEquals(new int[] {75, 99}, captor.getValue().getRanks());
    }

    @Test
    void analyseHistogramAcceptsJavaInfinityAndNegInfBuckets() {
        final AcceptableValue<BucketedValues> sink = mock(AcceptableValue.class);
        when(meterSystem.buildMetrics(anyString(), eq(BucketedValues.class))).thenReturn(sink);
        doNothing().when(meterSystem).create(anyString(), anyString(), any());
        doNothing().when(meterSystem).doStreamingCalculation(any());

        final Analyzer analyzer = Analyzer.build(
            "http_latency_histogram",
            null,
            "http_request_duration_bucket.histogram().service(['service'], Layer.GENERAL)",
            meterSystem
        );

        final ImmutableMap<String, SampleFamily> input = of(
            "http_request_duration_bucket",
            SampleFamilyBuilder.newBuilder(
                sample("-Inf", 1),
                sample("0.025", 100),
                sample("Infinity", 120)
            ).build()
        );

        try (MockedStatic<MetricsStreamProcessor> ignored = mockStatic(MetricsStreamProcessor.class)) {
            final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
            ignored.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
            assertDoesNotThrow(() -> analyzer.analyse(input));
        }

        final ArgumentCaptor<BucketedValues> captor = ArgumentCaptor.forClass(BucketedValues.class);
        verify(sink).accept(any(MeterEntity.class), captor.capture());
        assertArrayEquals(
            new long[] {Long.MIN_VALUE, 25L, Long.MAX_VALUE},
            captor.getValue().getBuckets()
        );
        assertEquals(3, captor.getValue().getValues().length);
    }

    private static Sample sample(final String le, final double value) {
        return Sample.builder()
                     .name("http_request_duration_bucket")
                     .labels(of("service", "ds-api", "le", le))
                     .value(value)
                     .timestamp(1_700_000_000_000L)
                     .build();
    }
}

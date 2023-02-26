/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import io.opentelemetry.proto.metrics.v1.ExponentialHistogram;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetryMetricRequestProcessorTest {

    private OtelMetricReceiverConfig config;

    private ModuleManager manager;

    private OpenTelemetryMetricRequestProcessor metricRequestProcessor;

    private Map<String, String> nodeLabels;

    @BeforeEach
    public void setUp() {
        manager = new ModuleManager();
        config = new OtelMetricReceiverConfig();
        metricRequestProcessor = new OpenTelemetryMetricRequestProcessor(manager, config);
        nodeLabels = new HashMap<>();
    }

    @Test
    public void testAdaptExponentialHistogram() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<OpenTelemetryMetricRequestProcessor> clazz = OpenTelemetryMetricRequestProcessor.class;
        Method adaptMetricsMethod = clazz.getDeclaredMethod("adaptMetrics", Map.class, Metric.class);
        adaptMetricsMethod.setAccessible(true);

        // number is 4, 7, 9
        ExponentialHistogramDataPoint.Buckets positiveBuckets = ExponentialHistogramDataPoint.Buckets.newBuilder()
                                                                                                     .setOffset(10)
                                                                                                     .addBucketCounts(
                                                                                                         1) // (0, 6.72]
                                                                                                     .addBucketCounts(
                                                                                                         1
                                                                                                     ) // (6.72, 8]
                                                                                                     .addBucketCounts(
                                                                                                         1
                                                                                                     ) // (8, 9.51]
                                                                                                     .build();
        // number is -14, -17, -18, -21
        ExponentialHistogramDataPoint.Buckets negativeBuckets = ExponentialHistogramDataPoint.Buckets.newBuilder()
                                                                                                     .setOffset(15)
                                                                                                     .addBucketCounts(
                                                                                                         1
                                                                                                     ) // (-16, -13.45]
                                                                                                     .addBucketCounts(
                                                                                                         2
                                                                                                     ) // (-19.02, -16]
                                                                                                     .addBucketCounts(
                                                                                                         1
                                                                                                     ) // (NEGATIVE_INFINITY, -19.02]
                                                                                                     .build();
        ExponentialHistogramDataPoint dataPoint = ExponentialHistogramDataPoint.newBuilder()
                                                                               .setCount(7)
                                                                               .setSum(-50)
                                                                               .setScale(2)
                                                                               .setPositive(positiveBuckets)
                                                                               .setNegative(negativeBuckets)
                                                                               .setTimeUnixNano(1000000)
                                                                               .build();
        ExponentialHistogram exponentialHistogram = ExponentialHistogram.newBuilder()
                                                                        .addDataPoints(dataPoint)
                                                                        .build();
        Metric metric = Metric.newBuilder()
                              .setName("test_metric")
                              .setExponentialHistogram(exponentialHistogram)
                              .build();

        Stream<Histogram> stream = (Stream<Histogram>) adaptMetricsMethod.invoke(
            metricRequestProcessor, nodeLabels, metric);
        List<Histogram> list = stream.toList();
        Histogram histogramMetric = list.get(0);
        assertEquals("test_metric", histogramMetric.getName());
        assertEquals(1, histogramMetric.getTimestamp());
        assertEquals(7, histogramMetric.getSampleCount());
        assertEquals(-50, histogramMetric.getSampleSum());

        // validate the key and value of bucket
        double base = Math.pow(2, Math.pow(2, -2));

        assertTrue(histogramMetric.getBuckets().containsKey(Math.pow(base, 11)));
        assertEquals(1, histogramMetric.getBuckets().get(Math.pow(base, 11)));

        assertTrue(histogramMetric.getBuckets().containsKey(Math.pow(base, 12)));
        assertEquals(1, histogramMetric.getBuckets().get(Math.pow(base, 12)));

        assertTrue(histogramMetric.getBuckets().containsKey(Double.POSITIVE_INFINITY));
        assertEquals(1, histogramMetric.getBuckets().get(Double.POSITIVE_INFINITY));

        assertTrue(histogramMetric.getBuckets().containsKey(-Math.pow(base, 15)));
        assertEquals(1, histogramMetric.getBuckets().get(-Math.pow(base, 15)));

        assertTrue(histogramMetric.getBuckets().containsKey(-Math.pow(base, 16)));
        assertEquals(2, histogramMetric.getBuckets().get(-Math.pow(base, 16)));

        assertTrue(histogramMetric.getBuckets().containsKey(-Math.pow(base, 17)));
        assertEquals(1, histogramMetric.getBuckets().get(-Math.pow(base, 17)));
    }
}

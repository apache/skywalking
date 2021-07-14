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

package org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters;

import io.prometheus.client.Metrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;

import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
public class ProtoMetricFamily2MetricsAdapter {
    protected final Metrics.MetricFamily metricFamily;
    private final ClusterManagerMetricsAdapter clusterManagerMetricsAdapter;

    public Stream<Metric> adapt() {
        switch (metricFamily.getType()) {
            case COUNTER:
                return metricFamily.getMetricList()
                                   .stream()
                                   .map(it -> Counter.builder()
                                                     .name(adaptMetricsName(it))
                                                     .value(it.getCounter().getValue())
                                                     .timestamp(adaptTimestamp(it))
                                                     .labels(adaptLabels(it))
                                                     .build());
            case GAUGE:
                return metricFamily.getMetricList()
                                   .stream()
                                   .map(it -> Gauge.builder()
                                                   .name(adaptMetricsName(it))
                                                   .value(adaptValue(it))
                                                   .timestamp(adaptTimestamp(it))
                                                   .labels(adaptLabels(it))
                                                   .build());
            case HISTOGRAM:
                return metricFamily.getMetricList()
                                   .stream()
                                   .map(it -> Histogram.builder()
                                                       .name(adaptMetricsName(it))
                                                       .labels(adaptLabels(it))
                                                       .sampleCount(it.getHistogram().getSampleCount())
                                                       .sampleSum(it.getHistogram().getSampleSum())
                                                       .buckets(buildBuckets(it.getHistogram().getBucketList()))
                                                       .build()
                                   );
            default:
                return Stream.of();
        }
    }

    public String adaptMetricsName(final Metrics.Metric metric) {
        if (metricFamily.getName().startsWith("cluster.")) {
            return clusterManagerMetricsAdapter.adaptMetricsName(metricFamily);
        }

        return metricFamily.getName();
    }

    public double adaptValue(final Metrics.Metric it) {
        return it.getGauge().getValue();
    }

    public Map<String, String> adaptLabels(final Metrics.Metric metric) {
        Map<String, String> labels = metric.getLabelList()
                                           .stream()
                                           .collect(toMap(Metrics.LabelPair::getName, Metrics.LabelPair::getValue));
        if (metricFamily.getName().startsWith("cluster.")) {
            return clusterManagerMetricsAdapter.adaptLabels(metricFamily, labels);
        }

        return labels;
    }

    public long adaptTimestamp(final Metrics.Metric metric) {
        long timestamp = metric.getTimestampMs();

        if (timestamp > 1000000000000000000L) {
            /*
             * Several versions of envoy in istio.deps send timestamp in nanoseconds,
             * instead of milliseconds(protocol says).
             *
             * Sadly, but have to fix it forcefully.
             *
             * An example of timestamp is '1552303033488741055', clearly it is not in milliseconds.
             *
             * This should be removed in the future.
             */
            timestamp /= 1_000_000;
        }

        return timestamp;
    }

    private static Map<Double, Long> buildBuckets(List<Metrics.Bucket> buckets) {
        Map<Double, Long> result = new HashMap<>();
        for (final Metrics.Bucket bucket : buckets) {
            result.put(bucket.getUpperBound(), bucket.getCumulativeCount());
        }
        return result;
    }
}

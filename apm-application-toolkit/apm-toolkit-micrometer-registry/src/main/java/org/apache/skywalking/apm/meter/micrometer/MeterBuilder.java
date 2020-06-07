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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.apache.skywalking.apm.toolkit.meter.Histogram;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.apache.skywalking.apm.toolkit.meter.Percentile;

import java.time.Duration;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Help to build the meter
 */
public class MeterBuilder {

    /**
     * Build the histogram
     * @return return histogram if support
     */
    public static Optional<Histogram> buildHistogram(MeterId meterId, boolean supportsAggregablePercentiles,
                                                     DistributionStatisticConfig distributionStatisticConfig) {
        if (!distributionStatisticConfig.isPublishingHistogram()) {
            return Optional.empty();
        }

        final NavigableSet<Double> buckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);
        final List<Double> steps = buckets.stream().sorted(Double::compare)
            .map(t -> (double) Duration.ofNanos(t.longValue()).toMillis()).collect(Collectors.toList());

        final Histogram.Builder histogramBuilder = new Histogram.Builder(
            meterId.copyTo(meterId.getName() + "_histogram", MeterId.MeterType.HISTOGRAM)).steps(steps);
        if (distributionStatisticConfig.getMinimumExpectedValueAsDouble() != null) {
            histogramBuilder.exceptMinValue(Duration.ofNanos(
                distributionStatisticConfig.getMinimumExpectedValueAsDouble().longValue()).toMillis());
        }
        return Optional.of(histogramBuilder.build());
    }

    /**
     * Build the percentile
     * @return return percentile if support
     */
    public static Optional<Percentile> buildPercentile(MeterId meterId, DistributionStatisticConfig distributionStatisticConfig) {
        if (!distributionStatisticConfig.isPublishingPercentiles()) {
            return Optional.empty();
        }

        final Percentile percentile = new Percentile.Builder(
            meterId.copyTo(meterId.getName() + "_percentile", MeterId.MeterType.PERCENTILE)).build();
        return Optional.of(percentile);
    }
}

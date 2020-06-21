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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.util.TimeUtils;
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.Histogram;
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Help to build the meter
 */
public class MeterBuilder {

    /**
     * Build the counter
     */
    public static Counter buildCounter(MeterId meterId, SkywalkingConfig config) {
        return MeterFactory.counter(meterId)
            .mode(getCounterMode(meterId, config))
            .build();
    }

    /**
     * Get counter mode
     */
    public static Counter.Mode getCounterMode(MeterId meterId, SkywalkingConfig config) {
        return config.isRateCounter(meterId.getName()) ? Counter.Mode.RATE : Counter.Mode.INCREMENT;
    }

    /**
     * Build the histogram
     * @return return histogram if support
     */
    public static Optional<Histogram> buildHistogram(MeterId meterId, boolean supportsAggregablePercentiles,
                                                     DistributionStatisticConfig distributionStatisticConfig,
                                                     boolean useNanoTime) {
        if (!distributionStatisticConfig.isPublishingHistogram()) {
            return Optional.empty();
        }

        final NavigableSet<Double> buckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);
        final List<Double> steps = buckets.stream().sorted(Double::compare)
            .map(t -> useNanoTime ? TimeUtils.nanosToUnit(t, TimeUnit.MILLISECONDS) : t).collect(Collectors.toList());

        final Histogram.Builder histogramBuilder = MeterFactory.histogram(
            meterId.copyTo(meterId.getName() + "_histogram", MeterId.MeterType.HISTOGRAM)).steps(steps);
        final Double minimumExpectedValueAsDouble = distributionStatisticConfig.getMinimumExpectedValueAsDouble();
        if (minimumExpectedValueAsDouble != null) {
            histogramBuilder.minValue(useNanoTime ?
                TimeUtils.nanosToUnit(minimumExpectedValueAsDouble, TimeUnit.MILLISECONDS) : minimumExpectedValueAsDouble);
        }
        return Optional.of(histogramBuilder.build());
    }

    /**
     * Convert micrometer {@link Meter.Id} to skywalking {@link MeterId}
     */
    public static MeterId convertId(Meter.Id id, String name) {
        MeterId.MeterType type;
        switch (id.getType()) {
            case COUNTER:
                type = MeterId.MeterType.COUNTER;
                break;
            case GAUGE:
                type = MeterId.MeterType.GAUGE;
                break;
            default:
                // other meter need to use multiple customize meter
                type = MeterId.MeterType.HISTOGRAM;
                break;
        }
        final List<MeterId.Tag> tags = id.getTags().stream().map(t -> new MeterId.Tag(t.getKey(), t.getValue())).collect(Collectors.toList());
        final MeterId meterId = new MeterId(name, type, tags);
        return meterId;
    }

}

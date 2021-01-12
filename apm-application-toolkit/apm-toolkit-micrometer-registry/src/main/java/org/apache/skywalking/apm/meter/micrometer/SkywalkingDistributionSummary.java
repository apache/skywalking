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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.Histogram;
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.Optional;
import java.util.concurrent.atomic.DoubleAccumulator;

/**
 * Combine the meters to {@link io.micrometer.core.instrument.DistributionSummary}
 */
@SuppressWarnings("HidingField")
public class SkywalkingDistributionSummary extends AbstractDistributionSummary {

    /**
     * Summary record count
     */
    private final Counter counter;

    /**
     * Total summary count
     */
    private final Counter sum;

    /**
     * Max amount in this summary
     */
    private final Gauge max;
    private final DoubleAccumulator maxAdder;

    /**
     * Histogram of summary
     */
    private final Optional<Histogram> histogram;

    protected SkywalkingDistributionSummary(Id id, MeterId meterId, SkywalkingConfig config, Clock clock,
                                            DistributionStatisticConfig distributionStatisticConfig, double scale,
                                            boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);

        // meter base name
        String baseName = meterId.getName();

        this.counter = MeterBuilder.buildCounter(meterId.copyTo(baseName + "_count", MeterId.MeterType.COUNTER), config);
        this.sum = MeterBuilder.buildCounter(meterId.copyTo(baseName + "_sum", MeterId.MeterType.COUNTER), config);
        this.maxAdder = new DoubleAccumulator((a, b) -> a > b ? a : b, 0.000);
        this.max = MeterFactory.gauge(meterId.copyTo(baseName + "_max", MeterId.MeterType.GAUGE),
            () -> maxAdder.doubleValue()).build();

        this.histogram = MeterBuilder.buildHistogram(meterId, supportsAggregablePercentiles, distributionStatisticConfig, false);
    }

    @Override
    protected void recordNonNegative(double amount) {
        counter.increment(1d);
        this.sum.increment(amount);
        maxAdder.accumulate(amount);

        histogram.ifPresent(h -> h.addValue(amount));
    }

    @Override
    public long count() {
        return (long) counter.get();
    }

    @Override
    public double totalAmount() {
        return sum.get();
    }

    @Override
    public double max() {
        return max.get();
    }
}

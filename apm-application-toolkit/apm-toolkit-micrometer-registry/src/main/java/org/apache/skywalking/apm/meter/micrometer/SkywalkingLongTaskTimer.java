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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.concurrent.TimeUnit;

/**
 * Combine the meters to {@link io.micrometer.core.instrument.LongTaskTimer}
 */
public class SkywalkingLongTaskTimer extends DefaultLongTaskTimer {

    public SkywalkingLongTaskTimer(Id id, MeterId meterId, Clock clock, TimeUnit baseTimeUnit, DistributionStatisticConfig distributionStatisticConfig, boolean supportsAggregablePercentiles) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, supportsAggregablePercentiles);
        final String baseName = meterId.getName();

        MeterFactory.gauge(
            meterId.copyTo(baseName + "_active_count", MeterId.MeterType.GAUGE), () -> (double) activeTasks()).build();
        MeterFactory.gauge(
            meterId.copyTo(baseName + "_duration_sum", MeterId.MeterType.GAUGE), () -> duration(TimeUnit.MILLISECONDS)).build();
        MeterFactory.gauge(
            meterId.copyTo(baseName + "_max", MeterId.MeterType.GAUGE), () -> max(TimeUnit.MILLISECONDS)).build();
    }

}

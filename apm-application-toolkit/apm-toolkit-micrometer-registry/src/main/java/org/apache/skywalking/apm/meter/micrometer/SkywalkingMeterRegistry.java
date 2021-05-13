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
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;
import org.apache.skywalking.apm.toolkit.meter.MeterCenter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Skywalking adapt the micrometer registry.
 */
public class SkywalkingMeterRegistry extends MeterRegistry {

    private final SkywalkingConfig config;

    public SkywalkingMeterRegistry() {
        this(SkywalkingConfig.DEFAULT, Clock.SYSTEM);
    }

    public SkywalkingMeterRegistry(SkywalkingConfig config) {
        this(config, Clock.SYSTEM);
    }

    public SkywalkingMeterRegistry(Clock clock) {
        this(SkywalkingConfig.DEFAULT, clock);
    }

    public SkywalkingMeterRegistry(SkywalkingConfig config, Clock clock) {
        super(clock);
        this.config = config;
        config().namingConvention(NamingConvention.snakeCase);
        config().onMeterRemoved(this::onMeterRemoved);
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        final MeterId meterId = convertId(id);
        MeterFactory.gauge(meterId, () -> valueFunction.applyAsDouble(obj)).build();
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(Meter.Id id) {
        final MeterId meterId = convertId(id);
        return new SkywalkingCounter(id, MeterBuilder.buildCounter(meterId, config));
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        final MeterId meterId = convertId(id);
        return new SkywalkingLongTaskTimer(id, meterId, clock, TimeUnit.MILLISECONDS, distributionStatisticConfig, true);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        final MeterId meterId = convertId(id);
        return new SkywalkingTimer(id, meterId, config, clock, distributionStatisticConfig, pauseDetector, TimeUnit.MILLISECONDS, true);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        final MeterId meterId = convertId(id);
        return new SkywalkingDistributionSummary(id, meterId, config, clock, distributionStatisticConfig, scale, true);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        final MeterId meterId = convertId(id);
        final String baseName = meterId.getName();

        measurements.forEach(m -> {
            String meterName = baseName;
            boolean isCounter = false;
            switch (m.getStatistic()) {
                case TOTAL:
                case TOTAL_TIME:
                    meterName = baseName + "_sum";
                    isCounter = true;
                    break;
                case COUNT:
                    isCounter = true;
                    break;
                case MAX:
                    meterName = baseName + "_max";
                    break;
                case ACTIVE_TASKS:
                    meterName = baseName + "_active_count";
                    break;
                case DURATION:
                    meterName = baseName + "_duration_sum";
                    break;
                default:
                    break;
            }

            if (isCounter) {
                new SkywalkingCustomCounter.Builder(meterId.copyTo(meterName, MeterId.MeterType.COUNTER), m, config).build();
            } else {
                MeterFactory.gauge(meterId.copyTo(meterName, MeterId.MeterType.GAUGE), () -> m.getValue()).build();
            }
        });

        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        final MeterId meterId = convertId(id);
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        final String baseName = meterId.getName();

        MeterFactory.gauge(
            meterId.copyTo(baseName + "_count", MeterId.MeterType.GAUGE), () -> ft.count()).build();
        MeterFactory.gauge(
            meterId.copyTo(baseName + "_sum", MeterId.MeterType.GAUGE), () -> ft.totalTime(TimeUnit.MILLISECONDS)).build();
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        final MeterId meterId = convertId(id);
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);

        new SkywalkingCustomCounter.Builder(meterId, new Measurement(() -> countFunction.applyAsDouble(obj), Statistic.COUNT), config).build();
        return fc;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.DEFAULT;
    }

    /**
     * Notify on the meter has been removed
     */
    private void onMeterRemoved(Meter meter) {
        final MeterId meterId = convertId(meter.getId());
        MeterCenter.removeMeter(meterId);
    }

    /**
     * Convert the micrometer meter id to skywalking meter id
     */
    private MeterId convertId(Meter.Id id) {
        return MeterBuilder.convertId(id, getConventionName(id));
    }
}

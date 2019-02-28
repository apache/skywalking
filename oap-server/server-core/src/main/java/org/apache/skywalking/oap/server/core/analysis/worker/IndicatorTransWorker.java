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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.Objects;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class IndicatorTransWorker extends AbstractWorker<Indicator> {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorTransWorker.class);

    private final IndicatorPersistentWorker minutePersistenceWorker;
    private final IndicatorPersistentWorker hourPersistenceWorker;
    private final IndicatorPersistentWorker dayPersistenceWorker;
    private final IndicatorPersistentWorker monthPersistenceWorker;

    private CounterMetric aggregationMinCounter;
    private CounterMetric aggregationHourCounter;
    private CounterMetric aggregationDayCounter;
    private CounterMetric aggregationMonthCounter;

    public IndicatorTransWorker(ModuleManager moduleManager,
        String modelName,
        int workerId,
        IndicatorPersistentWorker minutePersistenceWorker,
        IndicatorPersistentWorker hourPersistenceWorker,
        IndicatorPersistentWorker dayPersistenceWorker,
        IndicatorPersistentWorker monthPersistenceWorker) {
        super(workerId);
        this.minutePersistenceWorker = minutePersistenceWorker;
        this.hourPersistenceWorker = hourPersistenceWorker;
        this.dayPersistenceWorker = dayPersistenceWorker;
        this.monthPersistenceWorker = monthPersistenceWorker;

        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        aggregationMinCounter = metricCreator.createCounter("indicator_aggregation", "The number of rows in aggregation",
            new MetricTag.Keys("metricName", "level", "dimensionality"), new MetricTag.Values(modelName, "2", "min"));
        aggregationHourCounter = metricCreator.createCounter("indicator_aggregation", "The number of rows in aggregation",
            new MetricTag.Keys("metricName", "level", "dimensionality"), new MetricTag.Values(modelName, "2", "hour"));
        aggregationDayCounter = metricCreator.createCounter("indicator_aggregation", "The number of rows in aggregation",
            new MetricTag.Keys("metricName", "level", "dimensionality"), new MetricTag.Values(modelName, "2", "day"));
        aggregationMonthCounter = metricCreator.createCounter("indicator_aggregation", "The number of rows in aggregation",
            new MetricTag.Keys("metricName", "level", "dimensionality"), new MetricTag.Values(modelName, "2", "month"));
    }

    @Override public void in(Indicator indicator) {
        if (Objects.nonNull(hourPersistenceWorker)) {
            aggregationMonthCounter.inc();
            hourPersistenceWorker.in(indicator.toHour());
        }
        if (Objects.nonNull(dayPersistenceWorker)) {
            aggregationDayCounter.inc();
            dayPersistenceWorker.in(indicator.toDay());
        }
        if (Objects.nonNull(monthPersistenceWorker)) {
            aggregationHourCounter.inc();
            monthPersistenceWorker.in(indicator.toMonth());
        }
        /**
         * Minute persistent must be at the end of all time dimensionalities
         * Because #toHour, #toDay, #toMonth include clone inside, which could avoid concurrency situation.
         */
        if (Objects.nonNull(minutePersistenceWorker)) {
            aggregationMinCounter.inc();
            minutePersistenceWorker.in(indicator);
        }
    }
}

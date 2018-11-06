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

    public IndicatorTransWorker(int workerId,
        IndicatorPersistentWorker minutePersistenceWorker,
        IndicatorPersistentWorker hourPersistenceWorker,
        IndicatorPersistentWorker dayPersistenceWorker,
        IndicatorPersistentWorker monthPersistenceWorker) {
        super(workerId);
        this.minutePersistenceWorker = minutePersistenceWorker;
        this.hourPersistenceWorker = hourPersistenceWorker;
        this.dayPersistenceWorker = dayPersistenceWorker;
        this.monthPersistenceWorker = monthPersistenceWorker;
    }

    @Override public void in(Indicator indicator) {
        if (Objects.nonNull(hourPersistenceWorker)) {
            hourPersistenceWorker.in(indicator.toHour());
        }
        if (Objects.nonNull(dayPersistenceWorker)) {
            dayPersistenceWorker.in(indicator.toDay());
        }
        if (Objects.nonNull(monthPersistenceWorker)) {
            monthPersistenceWorker.in(indicator.toMonth());
        }
        /**
         * Minute persistent must be at the end of all time dimensionalities
         * Because #toHour, #toDay, #toMonth include clone inside, which could avoid concurrency situation.
         */
        if (Objects.nonNull(minutePersistenceWorker)) {
            minutePersistenceWorker.in(indicator);
        }
    }
}

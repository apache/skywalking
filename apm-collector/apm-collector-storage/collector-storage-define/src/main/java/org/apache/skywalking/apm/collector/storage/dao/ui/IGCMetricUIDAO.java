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

package org.apache.skywalking.apm.collector.storage.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;

/**
 * Interface to be implemented for execute database query operation
 * from {@link GCMetricTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IGCMetricUIDAO extends DAO {

    /**
     * Young GC Trend describes the trend of young generation garbage collection in the given
     * duration, which represents by the DurationPoint list in the `step` Unit.
     * <p>SQL as: select COUNT, TIMES, DURATION from GC_METRIC where ID in (durationPoints), rule of
     * ID generation is "${durationPoint}_${instanceId}_${gcPhrase}",
     * {@link org.apache.skywalking.apm.network.proto.GCPhrase#NEW_VALUE}
     * <p>The young generation GC count
     * <p>The average young generation GC average duration formula is "DURATION / TIMES".
     * <p>Every element in return list must match DurationPoint list, which also means that,
     * the two list must be in same size, and index match.
     * <p>If some element of the return list can't be found, the implementor must set 0 as
     * default value.
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId the owner id of this GC metrics
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average young generation GC count and duration metrics.
     * @see org.apache.skywalking.apm.collector.storage.ui.common.Step
     * @see org.apache.skywalking.apm.network.proto.GCPhrase
     */
    List<Trend> getYoungGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints);

    /**
     * Old GC Trend describes the trend of old generation garbage collection in the given
     * duration, which represents by the DurationPoint list in the `step` Unit.
     * <p>SQL as: select COUNT, TIMES, DURATION from GC_METRIC where ID in (durationPoints), rule of
     * ID generation is "${durationPoint}_${instanceId}_${gcPhrase}",
     * {@link org.apache.skywalking.apm.network.proto.GCPhrase#OLD_VALUE}
     * <p>The old generation GC count
     * <p>The average old generation GC average duration formula is "DURATION / TIMES".
     * <p>Every element in return list must match DurationPoint list, which also means that,
     * the two list must be in same size, and index match.
     * <p>If some element of the return list can't be found, the implementor must set 0 as
     * default value.
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId the owner id of this GC metrics
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average old generation GC count and duration metrics.
     * @see org.apache.skywalking.apm.collector.storage.ui.common.Step
     * @see org.apache.skywalking.apm.network.proto.GCPhrase
     */
    List<Trend> getOldGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints);

    class Trend {
        private int averageCount;
        private int averageDuration;

        public Trend(int averageCount, int averageDuration) {
            this.averageCount = averageCount;
            this.averageDuration = averageDuration;
        }

        public int getAverageCount() {
            return averageCount;
        }

        public int getAverageDuration() {
            return averageDuration;
        }
    }
}

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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;

/**
 * Interface to be implemented for execute database query operation
 * from {@link MemoryMetricTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IMemoryMetricUIDAO extends DAO {

    /**
     * Heap memory Trend describes the trend of Memory in the given duration, which
     * represents by the DurationPoint list in the `step` Unit.
     * <p>SQL as: select MAX, USED, TIMES from MEMORY_METRIC where ID in (durationPoints),
     * rule of ID generation is "${durationPoint}_${instanceId}_${isHeap}".
     * {@link org.apache.skywalking.apm.collector.core.util.BooleanUtils#TRUE}
     * <p>The average usage percent formula is "MAX / TIMES" and "USED / TIMES"
     * <p>Every element in return list must match DurationPoint list, which also means that,
     * the two list must be in same size, and index match.
     * <p>If some element of the return list can't be found, the implementor must set 0 as
     * default value.
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId the owner id of this memory metrics
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average heap memory max and usage percent metrics.
     * @see org.apache.skywalking.apm.collector.storage.ui.common.Step
     * @see org.apache.skywalking.apm.collector.core.util.BooleanUtils
     */
    Trend getHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints);

    /**
     * Non heap memory Trend describes the trend of Memory in the given duration, which
     * represents by the DurationPoint list in the `step` Unit.
     * <p>SQL as: select MAX, USED, TIMES from MEMORY_METRIC where ID in (durationPoints),
     * rule of ID generation is "${durationPoint}_${instanceId}_${isHeap}".
     * {@link org.apache.skywalking.apm.collector.core.util.BooleanUtils#FALSE}
     * <p>The average usage percent formula is "MAX / TIMES" and "USED / TIMES"
     * <p>Every element in return list must match DurationPoint list, which also means that,
     * the two list must be in same size, and index match.
     * <p>If some element of the return list can't be found, the implementor must set 0 as
     * default value.
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId the owner id of this memory metrics
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average non heap memory max and usage percent metrics.
     * @see org.apache.skywalking.apm.collector.storage.ui.common.Step
     * @see org.apache.skywalking.apm.collector.core.util.BooleanUtils
     */
    Trend getNoHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints);

    class Trend {
        private List<Integer> metrics;
        private List<Integer> maxMetrics;

        public Trend() {
            this.metrics = new LinkedList<>();
            this.maxMetrics = new LinkedList<>();
        }

        public List<Integer> getMetrics() {
            return metrics;
        }

        public List<Integer> getMaxMetrics() {
            return maxMetrics;
        }
    }
}

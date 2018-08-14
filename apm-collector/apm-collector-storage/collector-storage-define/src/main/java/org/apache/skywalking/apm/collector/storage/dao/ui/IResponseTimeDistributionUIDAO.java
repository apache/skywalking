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
import org.apache.skywalking.apm.collector.storage.ui.common.Step;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IResponseTimeDistributionUIDAO extends DAO {

    /**
     * <p>SQL as: select CALLS, ERROR_CALLS, SUCCESS_CALLS from RESPONSE_TIME_DISTRIBUTION
     * where ID in (${responseTimeSteps}),
     *
     * <p>Rule of ID generation is "${responseTimeStep.getDurationPoint}_${responseTimeStep.getStep}",
     *
     * <p>Every element in return list must match ResponseTimeStep list, which also means that,
     * the two list must be in same size, and yAxis match.
     *
     * <p>If some element of the return list can't be found, the implementor must set 0 as
     * default value.
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param step the step which represent time formats
     * @param responseTimeSteps the time points in the time span
     * @see org.apache.skywalking.apm.collector.storage.ui.common.Step
     */
    void loadMetrics(Step step, List<ResponseTimeStep> responseTimeSteps);

    class ResponseTimeStep {
        private final int step;
        private final long durationPoint;
        private final int yAxis;
        private long calls;
        private long errorCalls;
        private long successCalls;

        public ResponseTimeStep(long durationPoint, int yAxis, int step) {
            this.step = step;
            this.durationPoint = durationPoint;
            this.yAxis = yAxis;
        }

        public int getStep() {
            return step;
        }

        public long getDurationPoint() {
            return durationPoint;
        }

        public int getyAxis() {
            return yAxis;
        }

        public long getCalls() {
            return calls;
        }

        public void setCalls(long calls) {
            this.calls = calls;
        }

        public long getErrorCalls() {
            return errorCalls;
        }

        public void setErrorCalls(long errorCalls) {
            this.errorCalls = errorCalls;
        }

        public long getSuccessCalls() {
            return successCalls;
        }

        public void setSuccessCalls(long successCalls) {
            this.successCalls = successCalls;
        }
    }
}

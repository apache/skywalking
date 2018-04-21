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
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IServiceReferenceMetricUIDAO extends DAO {

    /**
     * Returns the service reference metrics which call the given service id
     * that collected between start time bucket and end time bucket.
     *
     * <p>SQL as: select FRONT_SERVICE_ID, sum(TRANSACTION_CALLS), sum(TRANSACTION_ERROR_CALLS),
     * sum(TRANSACTION_DURATION_SUM), sum(TRANSACTION_ERROR_DURATION_SUM)
     * from SERVICE_REFERENCE_METRIC
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * and SOURCE_VALUE = ${metricSource}
     * and BEHIND_SERVICE_ID = ${behindServiceId}
     * group by FRONT_SERVICE_ID
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param metricSource source of this metric, server side or client side
     * @param behindServiceId the callee service id
     * @return not nullable result list
     */
    List<ServiceReferenceMetric> getFrontServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource, int behindServiceId);

    /**
     * Returns the service reference metrics which call from the given service id
     * that collected between start time bucket and end time bucket.
     *
     * <p>SQL as: select FRONT_SERVICE_ID, sum(TRANSACTION_CALLS), sum(TRANSACTION_ERROR_CALLS),
     * sum(TRANSACTION_DURATION_SUM), sum(TRANSACTION_ERROR_DURATION_SUM)
     * from SERVICE_REFERENCE_METRIC
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * and SOURCE_VALUE = ${metricSource}
     * and BEHIND_SERVICE_ID = ${frontServiceId}
     * group by BEHIND_SERVICE_ID
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param metricSource source of this metric, server side or client side
     * @param frontServiceId the caller service id
     * @return not nullable result list
     */
    List<ServiceReferenceMetric> getBehindServices(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource, int frontServiceId);

    class ServiceReferenceMetric {
        private int source;
        private int target;
        private long calls;
        private long errorCalls;
        private long durations;
        private long errorDurations;

        public int getSource() {
            return source;
        }

        public void setSource(int source) {
            this.source = source;
        }

        public int getTarget() {
            return target;
        }

        public void setTarget(int target) {
            this.target = target;
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

        public long getDurations() {
            return durations;
        }

        public void setDurations(long durations) {
            this.durations = durations;
        }

        public long getErrorDurations() {
            return errorDurations;
        }

        public void setErrorDurations(long errorDurations) {
            this.errorDurations = errorDurations;
        }
    }
}

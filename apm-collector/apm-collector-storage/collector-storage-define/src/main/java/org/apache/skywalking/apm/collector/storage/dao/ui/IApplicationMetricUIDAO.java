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
import org.apache.skywalking.apm.collector.storage.ui.overview.ApplicationTPS;

/**
 * @author peng-yongsheng
 */
public interface IApplicationMetricUIDAO extends DAO {
    List<ApplicationTPS> getTopNApplicationThroughput(Step step, long startTimeBucket, long endTimeBucket,
        int betweenSecond, int topN, MetricSource metricSource);

    List<ApplicationMetric> getApplications(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource);

    class ApplicationMetric {
        private int id;
        private long calls;
        private long errorCalls;
        private long durations;
        private long errorDurations;
        private long satisfiedCount;
        private long toleratingCount;
        private long frustratedCount;

        public void setId(int id) {
            this.id = id;
        }

        public void setCalls(long calls) {
            this.calls = calls;
        }

        public void setErrorCalls(long errorCalls) {
            this.errorCalls = errorCalls;
        }

        public void setDurations(long durations) {
            this.durations = durations;
        }

        public void setErrorDurations(long errorDurations) {
            this.errorDurations = errorDurations;
        }

        public int getId() {
            return id;
        }

        public long getCalls() {
            return calls;
        }

        public long getErrorCalls() {
            return errorCalls;
        }

        public long getDurations() {
            return durations;
        }

        public long getErrorDurations() {
            return errorDurations;
        }

        public long getSatisfiedCount() {
            return satisfiedCount;
        }

        public void setSatisfiedCount(long satisfiedCount) {
            this.satisfiedCount = satisfiedCount;
        }

        public long getToleratingCount() {
            return toleratingCount;
        }

        public void setToleratingCount(long toleratingCount) {
            this.toleratingCount = toleratingCount;
        }

        public long getFrustratedCount() {
            return frustratedCount;
        }

        public void setFrustratedCount(long frustratedCount) {
            this.frustratedCount = frustratedCount;
        }
    }
}

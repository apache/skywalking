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
 * @author peng-yongsheng
 */
public interface IApplicationReferenceMetricUIDAO extends DAO {

    List<ApplicationReferenceMetric> getReferences(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource, Integer... applicationIds);

    class ApplicationReferenceMetric {
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

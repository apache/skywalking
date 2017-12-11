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


package org.apache.skywalking.apm.collector.storage.dao;

import com.google.gson.JsonArray;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;

/**
 * @author peng-yongsheng
 */
public interface IInstanceMetricUIDAO extends DAO {
    InstanceMetric get(long[] timeBuckets, int instanceId);

    long getTpsMetric(int instanceId, long timeBucket);

    JsonArray getTpsMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    long getRespTimeMetric(int instanceId, long timeBucket);

    JsonArray getRespTimeMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    class InstanceMetric {
        private final int instanceId;
        private final long calls;
        private final long durationSum;

        public InstanceMetric(int instanceId, long calls, long durationSum) {
            this.instanceId = instanceId;
            this.calls = calls;
            this.durationSum = durationSum;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public long getCalls() {
            return calls;
        }

        public long getDurationSum() {
            return durationSum;
        }
    }
}

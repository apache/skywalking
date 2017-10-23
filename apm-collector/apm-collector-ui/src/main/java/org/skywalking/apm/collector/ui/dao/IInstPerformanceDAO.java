/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author peng-yongsheng
 */
public interface IInstPerformanceDAO {
    InstPerformance get(long[] timeBuckets, int instanceId);

    int getTpsMetric(int instanceId, long timeBucket);

    JsonArray getTpsMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    int getRespTimeMetric(int instanceId, long timeBucket);

    JsonArray getRespTimeMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    class InstPerformance {
        private final int instanceId;
        private final int calls;
        private final long costTotal;

        public InstPerformance(int instanceId, int calls, long costTotal) {
            this.instanceId = instanceId;
            this.calls = calls;
            this.costTotal = costTotal;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public int getCalls() {
            return calls;
        }

        public long getCostTotal() {
            return costTotal;
        }
    }
}

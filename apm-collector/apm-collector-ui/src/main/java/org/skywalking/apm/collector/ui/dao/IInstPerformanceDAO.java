package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author pengys5
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

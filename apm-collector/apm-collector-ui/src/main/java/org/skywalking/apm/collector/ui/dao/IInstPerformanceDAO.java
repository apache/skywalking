package org.skywalking.apm.collector.ui.dao;

import java.util.List;

/**
 * @author pengys5
 */
public interface IInstPerformanceDAO {
    List<InstPerformance> getMultiple(long timestamp, int applicationId);

    class InstPerformance {
        private final int instanceId;
        private final int callTimes;
        private final long costTotal;

        public InstPerformance(int instanceId, int callTimes, long costTotal) {
            this.instanceId = instanceId;
            this.callTimes = callTimes;
            this.costTotal = costTotal;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public int getCallTimes() {
            return callTimes;
        }

        public long getCostTotal() {
            return costTotal;
        }
    }
}

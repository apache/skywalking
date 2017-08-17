package org.skywalking.apm.collector.ui.dao;

import java.util.List;

/**
 * @author pengys5
 */
public interface IInstanceDAO {
    Long lastHeartBeatTime();

    Long instanceLastHeartBeatTime(long applicationInstanceId);

    List<Application> getApplications(long time);

    class Application {
        private final int applicationId;
        private final long count;

        public Application(int applicationId, long count) {
            this.applicationId = applicationId;
            this.count = count;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public long getCount() {
            return count;
        }
    }
}

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import java.util.List;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;

/**
 * @author pengys5
 */
public interface IInstanceDAO {
    Long lastHeartBeatTime();

    Long instanceLastHeartBeatTime(long applicationInstanceId);

    JsonArray getApplications(long startTime, long endTime);

    InstanceDataDefine.Instance getInstance(int instanceId);

    List<InstanceDataDefine.Instance> getInstances(int applicationId, long timeBucket);

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

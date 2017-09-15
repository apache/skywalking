package org.skywalking.apm.collector.agentregister.worker.instance.dao;

import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class InstanceH2DAO extends H2DAO implements IInstanceDAO {
    @Override public int getInstanceId(int applicationId, String agentUUID) {
        return 0;
    }

    @Override public int getMaxInstanceId() {
        return 0;
    }

    @Override public int getMinInstanceId() {
        return 0;
    }

    @Override public void save(InstanceDataDefine.Instance instance) {

    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {

    }

    @Override public int getApplicationId(int applicationInstanceId) {
        return 0;
    }
}

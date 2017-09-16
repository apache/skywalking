package org.skywalking.apm.collector.agentregister.worker.instance.dao;

import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;

/**
 * @author pengys5
 */
public interface IInstanceDAO {
    int getInstanceId(int applicationId, String agentUUID);

    int getMaxInstanceId();

    int getMinInstanceId();

    void save(InstanceDataDefine.Instance instance);

    void updateHeartbeatTime(int instanceId, long heartbeatTime);

    int getApplicationId(int applicationInstanceId);
}

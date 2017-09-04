package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class InstanceH2DAO extends H2DAO implements IInstanceDAO {
    @Override public Long lastHeartBeatTime() {
        return null;
    }

    @Override public Long instanceLastHeartBeatTime(long applicationInstanceId) {
        return null;
    }

    @Override public JsonArray getApplications(long startTime, long endTime) {
        return null;
    }

    @Override public InstanceDataDefine.Instance getInstance(int instanceId) {
        return null;
    }
}

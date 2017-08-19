package org.skywalking.apm.collector.ui.dao;

import java.util.List;
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

    @Override public List<Application> getApplications(long time) {
        return null;
    }
}

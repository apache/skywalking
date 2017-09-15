package org.skywalking.apm.collector.agentregister.worker.application.dao;

import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ApplicationH2DAO extends H2DAO implements IApplicationDAO {

    @Override public int getApplicationId(String applicationCode) {
        H2Client client = getClient();
        return 100;
    }

    @Override public int getMaxApplicationId() {
        return 0;
    }

    @Override public int getMinApplicationId() {
        return 0;
    }

    @Override public void save(ApplicationDataDefine.Application application) {

    }
}

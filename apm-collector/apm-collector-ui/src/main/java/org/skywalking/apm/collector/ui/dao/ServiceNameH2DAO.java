package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ServiceNameH2DAO extends H2DAO implements IServiceNameDAO {

    @Override public String getServiceName(int serviceId) {
        return null;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
        return 0;
    }
}

package org.skywalking.apm.collector.agentregister.worker.servicename.dao;

import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class ServiceNameH2DAO extends H2DAO implements IServiceNameDAO {

    @Override public int getServiceId(int applicationId, String serviceName) {
        return 0;
    }

    @Override public int getMaxServiceId() {
        return 0;
    }

    @Override public int getMinServiceId() {
        return 0;
    }

    @Override public String getServiceName(int serviceId) {
        return null;
    }

    @Override public void save(ServiceNameDataDefine.ServiceName serviceName) {

    }
}

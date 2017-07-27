package org.skywalking.apm.collector.agentstream.worker.register.servicename.dao;

import org.skywalking.apm.collector.agentstream.worker.register.servicename.ServiceNameDataDefine;

/**
 * @author pengys5
 */
public interface IServiceNameDAO {
    int getServiceId(int applicationId, String serviceName);

    int getMaxServiceId();

    int getMinServiceId();

    void save(ServiceNameDataDefine.ServiceName serviceName);
}

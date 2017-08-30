package org.skywalking.apm.collector.ui.dao;

/**
 * @author pengys5
 */
public interface IServiceNameDAO {
    String getServiceName(int serviceId);

    int getServiceId(int applicationId, String serviceName);
}

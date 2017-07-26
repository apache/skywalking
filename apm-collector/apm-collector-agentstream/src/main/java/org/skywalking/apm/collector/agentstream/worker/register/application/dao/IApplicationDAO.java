package org.skywalking.apm.collector.agentstream.worker.register.application.dao;

import org.skywalking.apm.collector.agentstream.worker.register.application.ApplicationDataDefine;

/**
 * @author pengys5
 */
public interface IApplicationDAO {
    int getApplicationId(String applicationCode);

    int getMaxApplicationId();

    int getMinApplicationId();

    void save(ApplicationDataDefine.Application application);
}

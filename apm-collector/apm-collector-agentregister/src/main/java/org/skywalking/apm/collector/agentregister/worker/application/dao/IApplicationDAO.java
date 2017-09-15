package org.skywalking.apm.collector.agentregister.worker.application.dao;

import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;

/**
 * @author pengys5
 */
public interface IApplicationDAO {
    int getApplicationId(String applicationCode);

    int getMaxApplicationId();

    int getMinApplicationId();

    void save(ApplicationDataDefine.Application application);
}

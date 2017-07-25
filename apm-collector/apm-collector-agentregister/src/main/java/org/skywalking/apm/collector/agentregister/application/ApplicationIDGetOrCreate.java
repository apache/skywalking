package org.skywalking.apm.collector.agentregister.application;

import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ApplicationIDGetOrCreate {

    public int getOrCreate(String applicationCode) {
        IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
        return dao.getApplicationId(applicationCode);
    }
}

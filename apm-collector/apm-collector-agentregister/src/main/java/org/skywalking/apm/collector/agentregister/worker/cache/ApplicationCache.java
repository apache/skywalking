package org.skywalking.apm.collector.agentregister.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentregister.worker.application.dao.IApplicationDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ApplicationCache {

    private static Cache<String, Integer> CACHE = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    public static int get(String applicationCode) {
        int applicationId = 0;
        try {
            applicationId = CACHE.get(applicationCode, () -> {
                IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
                return dao.getApplicationId(applicationCode);
            });
        } catch (Throwable e) {
            return applicationId;
        }

        if (applicationId == 0) {
            IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
            applicationId = dao.getApplicationId(applicationCode);
            if (applicationId != 0) {
                CACHE.put(applicationCode, applicationId);
            }
        }
        return applicationId;
    }
}

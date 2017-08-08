package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentstream.worker.register.application.dao.IApplicationDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ApplicationCache {

    private static Cache<String, Integer> CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static int get(String applicationCode) {
        try {
            return CACHE.get(applicationCode, () -> {
                IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
                return dao.getApplicationId(applicationCode);
            });
        } catch (Throwable e) {
            return 0;
        }
    }
}

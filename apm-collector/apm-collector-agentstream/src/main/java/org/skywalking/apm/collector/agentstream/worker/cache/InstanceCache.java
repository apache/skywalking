package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentregister.worker.instance.dao.IInstanceDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class InstanceCache {

    private static Cache<Integer, Integer> CACHE = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    public static int get(int applicationInstanceId) {
        try {
            return CACHE.get(applicationInstanceId, () -> {
                IInstanceDAO dao = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
                return dao.getApplicationId(applicationInstanceId);
            });
        } catch (Throwable e) {
            return 0;
        }
    }
}

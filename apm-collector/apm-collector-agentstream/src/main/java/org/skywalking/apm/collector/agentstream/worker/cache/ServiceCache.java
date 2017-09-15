package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentregister.worker.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ServiceCache {

    private static Cache<Integer, String> CACHE = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(20000).build();

    public static String getServiceName(int serviceId) {
        try {
            return CACHE.get(serviceId, () -> {
                IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
                return dao.getServiceName(serviceId);
            });
        } catch (Throwable e) {
            return Const.EMPTY_STRING;
        }
    }
}

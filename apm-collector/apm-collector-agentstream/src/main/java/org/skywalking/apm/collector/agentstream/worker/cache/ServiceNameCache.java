package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.stream.worker.util.Const;
import org.skywalking.apm.collector.agentstream.worker.register.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ServiceNameCache {

    private static Cache<String, Integer> CACHE = CacheBuilder.newBuilder().maximumSize(2000).build();

    public static int get(int applicationId, String serviceName) {
        try {
            return CACHE.get(applicationId + Const.ID_SPLIT + serviceName, () -> {
                IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
                return dao.getServiceId(applicationId, serviceName);
            });
        } catch (Throwable e) {
            return 0;
        }
    }
}

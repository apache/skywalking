package org.skywalking.apm.collector.ui.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IServiceNameDAO;

/**
 * @author pengys5
 */
public class ServiceNameCache {

    //TODO size configuration
    private static Cache<Integer, String> CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static String get(int serviceId) {
        try {
            return CACHE.get(serviceId, () -> {
                IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
                return dao.getServiceName(serviceId);
            });
        } catch (Throwable e) {
            return Const.EXCEPTION;
        }
    }

    public static String getForUI(int serviceId) {
        String serviceName = get(serviceId);
        if (serviceName.equals("Unknown")) {
            IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
            serviceName = dao.getServiceName(serviceId);
            CACHE.put(serviceId, serviceName);
        }
        return serviceName;
    }
}

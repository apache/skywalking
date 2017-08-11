package org.skywalking.apm.collector.ui.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IApplicationDAO;

/**
 * @author pengys5
 */
public class ApplicationCache {

    //TODO size configuration
    private static Cache<Integer, String> CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static String get(int applicationId) {
        try {
            return CACHE.get(applicationId, () -> {
                IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
                return dao.getApplicationCode(applicationId);
            });
        } catch (Throwable e) {
            return Const.EXCEPTION;
        }
    }
}

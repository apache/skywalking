package org.skywalking.apm.collector.agentstream.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.agentstream.worker.Const;
import org.skywalking.apm.collector.agentstream.worker.node.component.dao.INodeComponentDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;

/**
 * @author pengys5
 */
public class ComponentCache {

    private static Cache<String, Integer> CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static int get(int applicationId, String componentName) {
        try {
            return CACHE.get(applicationId + Const.ID_SPLIT + componentName, () -> {
                INodeComponentDAO dao = (INodeComponentDAO)DAOContainer.INSTANCE.get(INodeComponentDAO.class.getName());
                return dao.getComponentId(applicationId, componentName);
            });
        } catch (Throwable e) {
            return 0;
        }
    }
}

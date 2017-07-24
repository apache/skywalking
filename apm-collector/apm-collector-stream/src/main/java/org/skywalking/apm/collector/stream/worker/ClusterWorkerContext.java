package org.skywalking.apm.collector.stream.worker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterWorkerContext extends WorkerContext {
    private final Logger logger = LoggerFactory.getLogger(ClusterWorkerContext.class);

    private Map<String, AbstractWorkerProvider> providers = new ConcurrentHashMap<>();

    @Override
    public AbstractWorkerProvider findProvider(Role role) throws ProviderNotFoundException {
        logger.debug("find role of %s provider from ClusterWorkerContext", role.roleName());
        if (providers.containsKey(role.roleName())) {
            return providers.get(role.roleName());
        } else {
            throw new ProviderNotFoundException("role=" + role.roleName() + ", no available provider.");
        }
    }

    @Override
    public void putProvider(AbstractWorkerProvider provider) throws UsedRoleNameException {
        logger.debug("put role of %s provider into ClusterWorkerContext", provider.role().roleName());
        if (providers.containsKey(provider.role().roleName())) {
            throw new UsedRoleNameException("provider with role=" + provider.role().roleName() + " duplicate each other.");
        } else {
            providers.put(provider.role().roleName(), provider);
        }
    }
}

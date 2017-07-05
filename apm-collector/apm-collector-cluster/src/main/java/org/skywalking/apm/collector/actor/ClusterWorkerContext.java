package org.skywalking.apm.collector.actor;

import akka.actor.ActorSystem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.rpc.RPCAddressContext;

/**
 * @author pengys5
 */
public class ClusterWorkerContext extends WorkerContext {
    private Logger logger = LogManager.getFormatterLogger(ClusterWorkerContext.class);

    private final ActorSystem akkaSystem;
    private Map<String, AbstractWorkerProvider> providers = new ConcurrentHashMap<>();
    private RPCAddressContext rpcContext = new RPCAddressContext();

    public ClusterWorkerContext(ActorSystem akkaSystem) {
        this.akkaSystem = akkaSystem;
    }

    public ActorSystem getAkkaSystem() {
        return akkaSystem;
    }

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

    public RPCAddressContext getRpcContext() {
        return rpcContext;
    }
}

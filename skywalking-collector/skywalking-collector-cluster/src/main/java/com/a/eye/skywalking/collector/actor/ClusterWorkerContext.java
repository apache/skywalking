package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public class ClusterWorkerContext extends WorkerContext {

    private Logger logger = LogManager.getFormatterLogger(ClusterWorkerContext.class);

    private final ActorSystem akkaSystem;

    private Map<String, AbstractWorkerProvider> providers = new ConcurrentHashMap<>();

    public ClusterWorkerContext(ActorSystem akkaSystem) {
        this.akkaSystem = akkaSystem;
    }

    public ActorSystem getAkkaSystem() {
        return akkaSystem;
    }

    @Override
    public AbstractWorkerProvider findProvider(Role role) throws ProviderNotFountException {
        logger.debug("find role of %s provider from ClusterWorkerContext", role.name());
        if (providers.containsKey(role.name())) {
            return providers.get(role.name());
        } else {
            throw new ProviderNotFountException("role=" + role.name() + ", no available provider.");
        }
    }

    @Override
    public void putProvider(AbstractWorkerProvider provider) throws DuplicateProviderException {
        logger.debug("put role of %s provider into ClusterWorkerContext", provider.role().name());
        if (providers.containsKey(provider.role().name())) {
            throw new DuplicateProviderException("provider with role=" + provider.role().name() + " duplicate each other.");
        } else {
            providers.put(provider.role().name(), provider);
        }
    }
}

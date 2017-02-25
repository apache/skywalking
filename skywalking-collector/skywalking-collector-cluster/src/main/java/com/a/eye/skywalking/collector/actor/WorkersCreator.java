package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum WorkersCreator {
    INSTANCE;

    public void boot(ActorSystem system) {
        ServiceLoader<AbstractWorkerProvider> serviceLoader = ServiceLoader.load(AbstractWorkerProvider.class);
        for (AbstractWorkerProvider provider : serviceLoader) {
            provider.createWorker(system);
        }
    }
}

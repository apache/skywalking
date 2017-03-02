package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;

import java.util.ServiceLoader;

/**
 * <code>WorkersCreator</code> is a util that use Java Spi to create
 * workers by META-INF config file.
 *
 * @author pengys5
 */
public enum WorkersCreator {
    INSTANCE;

    /**
     * create worker to use Java Spi.
     *
     * @param system is create by akka {@link ActorSystem}
     */
    public void boot(ActorSystem system) {
        ServiceLoader<AbstractWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractWorkerProvider.class);
        for (AbstractWorkerProvider provider : clusterServiceLoader) {
            provider.createWorker(system);
        }
    }
}

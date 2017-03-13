package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;

import java.util.ServiceLoader;

/**
 * <code>WorkersCreator</code> is a util that use Java Spi to create
 * workers by META-INF config file.
 *
 * @author pengys5
 */
public enum WorkersCreator {
    INSTANCE;

    private ILog logger = LogManager.getLogger(WorkersCreator.class);

    /**
     * create worker to use Java Spi.
     *
     * @param system is create by akka {@link ActorSystem}
     */
    public void boot(ActorSystem system) {
        system.actorOf(Props.create(WorkersListener.class), WorkersListener.WorkName);

        ServiceLoader<AbstractWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractWorkerProvider.class);
        for (AbstractWorkerProvider provider : clusterServiceLoader) {
            logger.info("create worker {%s} using java service loader", provider.workerClass().getName());
            provider.createWorker(system);
        }
    }
}

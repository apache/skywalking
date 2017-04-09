package com.a.eye.skywalking.collector;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import com.a.eye.skywalking.collector.config.ConfigInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public class CollectorSystem {
    private Logger logger = LogManager.getFormatterLogger(CollectorSystem.class);

    private ClusterWorkerContext clusterContext;

    public LookUp getClusterContext() {
        return clusterContext;
    }

    public void boot() throws UsedRoleNameException, ProviderNotFoundException, IOException, IllegalAccessException {
        ConfigInitializer.INSTANCE.initialize();
        createAkkaSystem();
        createListener();
        loadLocalProviders();
        createClusterWorkers();
    }

    private void createAkkaSystem() {
        ActorSystem akkaSystem = AkkaSystem.INSTANCE.create();
        clusterContext = new ClusterWorkerContext(akkaSystem);
    }

    private void createListener() {
        clusterContext.getAkkaSystem().actorOf(Props.create(WorkersListener.class, clusterContext), WorkersListener.WorkName);
    }

    private void createClusterWorkers() throws ProviderNotFoundException {
        ServiceLoader<AbstractClusterWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractClusterWorkerProvider.class);
        for (AbstractClusterWorkerProvider provider : clusterServiceLoader) {
            logger.info("create {%s} worker using java service loader", provider.workerNum());
            provider.setClusterContext(clusterContext);
            for (int i = 1; i <= provider.workerNum(); i++) {
                provider.create(AbstractWorker.noOwner());
            }
        }
    }

    private void loadLocalProviders() throws UsedRoleNameException {
        ServiceLoader<AbstractLocalWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractLocalWorkerProvider.class);
        for (AbstractLocalWorkerProvider provider : clusterServiceLoader) {
            logger.info("loadLocalProviders provider name: %s", provider.getClass().getName());
            provider.setClusterContext(clusterContext);
            clusterContext.putProvider(provider);
        }
    }
}

package com.a.eye.skywalking.collector;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public class CollectorSystem {
    private ILog logger = LogManager.getLogger(CollectorSystem.class);
    private ClusterWorkerContext clusterContext;

    public LookUp getClusterContext() {
        return clusterContext;
    }

    public void boot() throws UsedRoleNameException, ProviderNotFoundException {
        createAkkaSystem();
        createListener();
        loadLocalProviders();
        createClusterWorkers();
    }

    public void terminate() {
        clusterContext.getAkkaSystem().terminate();
    }

    private void createAkkaSystem() {
        ClusterConfigInitializer.initialize("collector.config");

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + ClusterConfig.Cluster.Current.hostname).
                withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port)).
                withFallback(ConfigFactory.parseString("akka.cluster.roles=" + ClusterConfig.Cluster.Current.roles)).
                withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + ClusterConfig.Cluster.seed_nodes)).
                withFallback(ConfigFactory.load("application.conf"));
        ActorSystem akkaSystem = ActorSystem.create("ClusterSystem", config);

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
            provider.setClusterContext(clusterContext);
            clusterContext.putProvider(provider);
        }
    }
}

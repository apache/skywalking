package com.a.eye.skywalking.collector;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.actor.AbstractClusterWorkerProvider;
import com.a.eye.skywalking.collector.actor.AbstractLocalWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.DuplicateProviderException;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum CollectorSystem {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(CollectorSystem.class);

    private ClusterWorkerContext clusterContext;

    public ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    public void boot() throws Exception {
        createAkkaSystem();
        createListener();
        createLocalProvider();

        createClusterWorker();
    }

    public void terminate() {
        clusterContext.getAkkaSystem().terminate();
    }

    private void createAkkaSystem() {
        ClusterConfigInitializer.initialize("collector.config");

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + ClusterConfig.Cluster.Current.hostname).
                withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port)).
                withFallback(ConfigFactory.parseString("akka.cluster.roles=" + ClusterConfig.Cluster.Current.roles)).
                withFallback(ConfigFactory.parseString("akka.actor.provider=" + ClusterConfig.Cluster.provider)).
                withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + ClusterConfig.Cluster.nodes)).
                withFallback(ConfigFactory.load("application.conf"));
        ActorSystem akkaSystem = ActorSystem.create("ClusterSystem", config);

        clusterContext = new ClusterWorkerContext(akkaSystem);
    }

    private void createListener() {
        clusterContext.getAkkaSystem().actorOf(Props.create(WorkersListener.class, clusterContext), WorkersListener.WorkName);
    }

    private void createClusterWorker() throws Exception {
        ServiceLoader<AbstractClusterWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractClusterWorkerProvider.class);
        for (AbstractClusterWorkerProvider provider : clusterServiceLoader) {
            logger.info("create {%s} worker {%s} using java service loader", provider.workerNum(), provider.workerClass().getName());
            for (int i = 1; i <= provider.workerNum(); i++) {
                provider.create(clusterContext, null);
            }
        }
    }

    private void createLocalProvider() throws DuplicateProviderException {
        ServiceLoader<AbstractLocalWorkerProvider> clusterServiceLoader = ServiceLoader.load(AbstractLocalWorkerProvider.class);
        for (AbstractLocalWorkerProvider provider : clusterServiceLoader) {
            clusterContext.putProvider(provider);
        }
    }
}

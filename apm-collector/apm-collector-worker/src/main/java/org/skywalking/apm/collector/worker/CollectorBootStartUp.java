package org.skywalking.apm.collector.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.CollectorSystem;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.worker.grpcserver.GRPCServer;
import org.skywalking.apm.collector.worker.httpserver.HttpServer;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.IndexCreator;
import org.skywalking.apm.collector.worker.storage.PersistenceTimer;

/**
 * @author pengys5
 */
public class CollectorBootStartUp {
    private static final Logger logger = LogManager.getFormatterLogger(CollectorBootStartUp.class);

    public static void main(String[] args) throws Exception {
        logger.info("collector system starting....");
        CollectorSystem collectorSystem = new CollectorSystem();
        collectorSystem.boot();
        EsClient.INSTANCE.boot();
        IndexCreator.INSTANCE.create();
        PersistenceTimer.INSTANCE.boot();
        HttpServer.INSTANCE.boot((ClusterWorkerContext) collectorSystem.getClusterContext());
        GRPCServer.INSTANCE.boot((ClusterWorkerContext) collectorSystem.getClusterContext());
    }
}

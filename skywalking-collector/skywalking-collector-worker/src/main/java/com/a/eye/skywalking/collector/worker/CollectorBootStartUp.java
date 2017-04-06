package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.CollectorSystem;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.worker.httpserver.HttpServer;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.IndexCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class CollectorBootStartUp {

    private static Logger logger = LogManager.getFormatterLogger(CollectorBootStartUp.class);

    public static void main(String[] args) throws Exception {
        logger.info("collector system starting....");
        ClusterConfigInitializer.initialize("collector.config");

        CollectorSystem collectorSystem = new CollectorSystem();
        collectorSystem.boot();
        EsClient.INSTANCE.boot();
        IndexCreator.INSTANCE.create();
        HttpServer.INSTANCE.boot((ClusterWorkerContext) collectorSystem.getClusterContext());
    }
}

package org.skywalking.apm.collector.worker.grpcserver;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.worker.config.GRPCConfig;

/**
 * @author pengys5
 */
public enum GRPCServer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(GRPCServer.class);

    private Server server;

    public void boot(ClusterWorkerContext clusterContext) throws Exception {
        start(clusterContext);
        blockUntilShutdown();
    }

    private void start(ClusterWorkerContext clusterContext) throws Exception {
        int port = Integer.valueOf(GRPCConfig.GRPC.PORT);
        NettyServerBuilder nettyServerBuilder = NettyServerBuilder.forPort(port);
        ServicesCreator.INSTANCE.boot(nettyServerBuilder, clusterContext);
        server = nettyServerBuilder.build().start();
        logger.info("Server started, listening on " + port);
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}

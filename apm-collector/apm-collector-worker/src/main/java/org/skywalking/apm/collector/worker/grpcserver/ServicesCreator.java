package org.skywalking.apm.collector.worker.grpcserver;

import io.grpc.BindableService;
import io.grpc.netty.NettyServerBuilder;
import java.util.ServiceLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;

/**
 * @author pengys5
 */
public enum ServicesCreator {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(ServicesCreator.class);

    public void boot(NettyServerBuilder nettyServerBuilder,
        ClusterWorkerContext clusterContext) throws IllegalArgumentException, ProviderNotFoundException {
        ServiceLoader<BindableService> grpcServiceLoader = java.util.ServiceLoader.load(BindableService.class);
        for (BindableService service : grpcServiceLoader) {
            logger.info("add grpc service %s into netty server builder ", service.getClass().getSimpleName());
            nettyServerBuilder.addService(service);
            ((WorkerCaller)service).inject(clusterContext);
            ((WorkerCaller)service).preStart();
        }
    }
}

package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum ServletsCreator {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(ServletsCreator.class);

    public void boot(ServletContextHandler servletContextHandler, ClusterWorkerContext clusterContext) throws IllegalArgumentException, ProviderNotFoundException {
        ServiceLoader<AbstractReceiverProvider> receiverLoader = java.util.ServiceLoader.load(AbstractReceiverProvider.class);
        for (AbstractReceiverProvider provider : receiverLoader) {
            provider.setClusterContext(clusterContext);
            provider.createReceiver(servletContextHandler);
            logger.info("add receiver servlet mapping path: %s ", provider.servletPath());
        }

        ServiceLoader<AbstractSearcherProvider> searcherLoader = java.util.ServiceLoader.load(AbstractSearcherProvider.class);
        for (AbstractSearcherProvider provider : searcherLoader) {
            provider.setClusterContext(clusterContext);
            provider.createSearcher(servletContextHandler);
            logger.info("add searcher servlet mapping path: %s ", provider.servletPath());
        }
    }
}

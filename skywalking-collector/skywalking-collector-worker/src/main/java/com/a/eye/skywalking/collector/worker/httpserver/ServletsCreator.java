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

    public void boot(ServletContextHandler servletContextHandler,
        ClusterWorkerContext clusterContext) throws IllegalArgumentException, ProviderNotFoundException {
        ServiceLoader<AbstractPostProvider> receiverLoader = java.util.ServiceLoader.load(AbstractPostProvider.class);
        for (AbstractPostProvider provider : receiverLoader) {
            provider.setClusterContext(clusterContext);
            provider.create(servletContextHandler);
            logger.info("add post servlet mapping path: %s ", provider.servletPath());
        }

        ServiceLoader<AbstractGetProvider> searcherLoader = java.util.ServiceLoader.load(AbstractGetProvider.class);
        for (AbstractGetProvider provider : searcherLoader) {
            provider.setClusterContext(clusterContext);
            provider.create(servletContextHandler);
            logger.info("add get servlet mapping path: %s ", provider.servletPath());
        }
    }
}

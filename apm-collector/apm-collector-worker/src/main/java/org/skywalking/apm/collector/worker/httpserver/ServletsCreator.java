package org.skywalking.apm.collector.worker.httpserver;

import java.util.ServiceLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;

/**
 * The <code>ServletsCreator</code> is a servlet workers starter. Use SPI to create the instances implement {@link
 * AbstractGetProvider}, {@link AbstractPostProvider}, {@link AbstractStreamPostProvider}.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public enum ServletsCreator {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(ServletsCreator.class);

    /**
     * Use SPI to find the servlet workers provider then use the provider to create worker instances.
     *
     * @param servletContextHandler add a mapping between url and worker.
     * @param clusterContext the context contains remote worker reference.
     * @throws IllegalArgumentException
     * @throws ProviderNotFoundException
     */
    public void boot(ServletContextHandler servletContextHandler,
        ClusterWorkerContext clusterContext) throws IllegalArgumentException, ProviderNotFoundException {
        ServiceLoader<AbstractPostProvider> postServletLoader = java.util.ServiceLoader.load(AbstractPostProvider.class);
        for (AbstractPostProvider provider : postServletLoader) {
            provider.setClusterContext(clusterContext);
            provider.create(servletContextHandler);
            logger.info("add post servlet mapping path: %s ", provider.servletPath());
        }

        ServiceLoader<AbstractStreamPostProvider> streamPostServletLoader = java.util.ServiceLoader.load(AbstractStreamPostProvider.class);
        for (AbstractStreamPostProvider provider : streamPostServletLoader) {
            provider.setClusterContext(clusterContext);
            provider.create(servletContextHandler);
            logger.info("add stream post servlet mapping path: %s ", provider.servletPath());
        }

        ServiceLoader<AbstractGetProvider> getServletLoader = java.util.ServiceLoader.load(AbstractGetProvider.class);
        for (AbstractGetProvider provider : getServletLoader) {
            provider.setClusterContext(clusterContext);
            provider.create(servletContextHandler);
            logger.info("add get servlet mapping path: %s ", provider.servletPath());
        }
    }
}

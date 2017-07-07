package org.skywalking.apm.collector.worker.httpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.worker.config.HttpConfig;

import java.net.InetSocketAddress;

/**
 * @author pengys5
 */
public enum HttpServer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(HttpServer.class);

    public void boot(ClusterWorkerContext clusterContext) throws Exception {
        Server server = new Server(new InetSocketAddress(HttpConfig.Http.HOSTNAME, Integer.valueOf(HttpConfig.Http.PORT)));

        String contextPath = HttpConfig.Http.CONTEXTPATH;
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        logger.info("http server root context path: %s", contextPath);

        ServletsCreator.INSTANCE.boot(servletContextHandler, clusterContext);

        server.setHandler(servletContextHandler);
        server.start();
    }
}

package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * @author pengys5
 */
public enum HttpServer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(HttpServer.class);

    public void boot(ClusterWorkerContext clusterContext) throws Exception {
        Server server = new Server(7001);

        String contextPath = "/skywalking";
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        logger.info("http server root context path: %s", contextPath);

        ServletsCreator.INSTANCE.boot(servletContextHandler, clusterContext);

//        ServerConnector serverConnector = new ServerConnector(server);
//        serverConnector.setHost("127.0.0.1");
//        serverConnector.setPort(7001);
//        serverConnector.setIdleTimeout(5000);

        server.setHandler(servletContextHandler);
        server.start();
        server.join();
    }
}
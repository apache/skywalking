/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.server.jetty;

import java.util.Objects;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyServer implements Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(JettyServer.class);

    private org.eclipse.jetty.server.Server server;
    private ServletContextHandler servletContextHandler;
    private JettyServerConfig jettyServerConfig;

    public JettyServer(JettyServerConfig config) {
        this.jettyServerConfig = config;
    }

    @Override
    public String hostPort() {
        return jettyServerConfig.getHost() + ":" + jettyServerConfig.getPort();
    }

    @Override
    public String serverClassify() {
        return "Jetty";
    }

    @Override
    public void initialize() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(jettyServerConfig.getJettyMinThreads());
        threadPool.setMaxThreads(jettyServerConfig.getJettyMaxThreads());

        server = new org.eclipse.jetty.server.Server(threadPool);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setRequestHeaderSize(jettyServerConfig.getJettyHttpMaxRequestHeaderSize());

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        connector.setHost(jettyServerConfig.getHost());
        connector.setPort(jettyServerConfig.getPort());
        connector.setIdleTimeout(jettyServerConfig.getJettyIdleTimeOut());
        connector.setAcceptorPriorityDelta(jettyServerConfig.getJettyAcceptorPriorityDelta());
        connector.setAcceptQueueSize(jettyServerConfig.getJettyAcceptQueueSize());
        server.setConnectors(new Connector[] {connector});

        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(jettyServerConfig.getContextPath());
        LOGGER.info("http server root context path: {}", jettyServerConfig.getContextPath());

        server.setHandler(servletContextHandler);

        JettyDefaultHandler defaultHandler = new JettyDefaultHandler();
        ServletHolder defaultHolder = new ServletHolder();
        defaultHolder.setServlet(defaultHandler);

        servletContextHandler.addServlet(defaultHolder, defaultHandler.pathSpec());
    }

    public void addHandler(JettyHandler handler) {
        LOGGER.info(
            "Bind handler {} into jetty server {}:{}",
            handler.getClass().getSimpleName(), jettyServerConfig.getHost(), jettyServerConfig.getPort()
        );

        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet(handler);
        servletContextHandler.addServlet(servletHolder, handler.pathSpec());
    }

    @Override
    public boolean isSSLOpen() {
        return false;
    }

    @Override
    public boolean isStatusEqual(Server target) {
        return equals(target);
    }

    @Override
    public void start() throws ServerException {
        LOGGER.info("start server, host: {}, port: {}", jettyServerConfig.getHost(), jettyServerConfig.getPort());
        try {
            if (LOGGER.isDebugEnabled()) {
                if (servletContextHandler.getServletHandler() != null && servletContextHandler.getServletHandler()
                                                                                              .getServletMappings() != null) {
                    for (ServletMapping servletMapping : servletContextHandler.getServletHandler()
                                                                              .getServletMappings()) {
                        LOGGER.debug(
                            "jetty servlet mappings: {} register by {}", servletMapping.getPathSpecs(), servletMapping
                                .getServletName());
                    }
                }
            }

            server.start();
        } catch (Exception e) {
            throw new JettyServerException(e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        JettyServer that = (JettyServer) o;
        return jettyServerConfig.getPort() == that.jettyServerConfig.getPort() && Objects.equals(
            jettyServerConfig.getHost(), that.jettyServerConfig.getHost());
    }

    @Override
    public int hashCode() {
        return Objects.hash(jettyServerConfig.getHost(), jettyServerConfig.getPort());
    }

}

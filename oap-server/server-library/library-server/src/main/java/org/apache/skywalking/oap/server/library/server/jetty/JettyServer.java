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

import java.net.InetSocketAddress;
import java.util.Objects;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyServer implements Server {

    private static final Logger logger = LoggerFactory.getLogger(JettyServer.class);

    private final String host;
    private final int port;
    private final String contextPath;
    private final int selectorNum;
    private org.eclipse.jetty.server.Server server;
    private ServletContextHandler servletContextHandler;

    public JettyServer(String host, int port, String contextPath) {
        this(host, port, contextPath, -1);
    }

    public JettyServer(String host, int port, String contextPath, int selectorNum) {
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
        this.selectorNum = selectorNum;
    }

    @Override
    public String hostPort() {
        return host + ":" + port;
    }

    @Override
    public String serverClassify() {
        return "Jetty";
    }

    @Override
    public void initialize() {
        server = new org.eclipse.jetty.server.Server(new InetSocketAddress(host, port));

        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        logger.info("http server root context path: {}", contextPath);

        server.setHandler(servletContextHandler);
    }

    public void addHandler(JettyHandler handler) {
        logger.info("Bind handler {} into jetty server {}:{}", handler.getClass().getSimpleName(), host, port);

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
        logger.info("start server, host: {}, port: {}", host, port);
        try {
            if (logger.isDebugEnabled()) {
                if (servletContextHandler.getServletHandler() != null && servletContextHandler.getServletHandler()
                                                                                              .getServletMappings() != null) {
                    for (ServletMapping servletMapping : servletContextHandler.getServletHandler()
                                                                              .getServletMappings()) {
                        logger.debug("jetty servlet mappings: {} register by {}", servletMapping.getPathSpecs(), servletMapping
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
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}

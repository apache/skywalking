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


package org.apache.skywalking.apm.collector.server.jetty;

import java.net.InetSocketAddress;
import javax.servlet.http.HttpServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.apache.skywalking.apm.collector.server.Server;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.ServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JettyServer implements Server {

    private final Logger logger = LoggerFactory.getLogger(JettyServer.class);

    private final String host;
    private final int port;
    private final String contextPath;
    private org.eclipse.jetty.server.Server server;
    private ServletContextHandler servletContextHandler;

    public JettyServer(String host, int port, String contextPath) {
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
    }

    @Override public String hostPort() {
        return host + ":" + port;
    }

    @Override public String serverClassify() {
        return "Jetty";
    }

    @Override public void initialize() throws ServerException {
        server = new org.eclipse.jetty.server.Server(new InetSocketAddress(host, port));

        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        logger.info("http server root context path: {}", contextPath);

        server.setHandler(servletContextHandler);
    }

    @Override public void addHandler(ServerHandler handler) {
        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet((HttpServlet)handler);
        servletContextHandler.addServlet(servletHolder, ((JettyHandler)handler).pathSpec());
    }

    @Override public void start() throws ServerException {
        logger.info("start server, host: {}, port: {}", host, port);
        try {
            for (ServletMapping servletMapping : servletContextHandler.getServletHandler().getServletMappings()) {
                logger.info("jetty servlet mappings: {} register by {}", servletMapping.getPathSpecs(), servletMapping.getServletName());
            }
            server.start();
        } catch (Exception e) {
            throw new JettyServerException(e.getMessage(), e);
        }
    }
}

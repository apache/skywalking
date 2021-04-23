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

package org.apache.skywalking.apm.testcase.thrift.server.service;

import org.apache.skywalking.apm.testcase.thrift.protocol.GreeterService;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.net.InetSocketAddress;

public class HttpServer implements IServer {

    public static void main(String[] args) throws Exception {
        new HttpServer().start();
    }

    @Override
    public void start() throws Exception {
        Server jettyServer = new Server(new InetSocketAddress("0.0.0.0", Integer.valueOf(9080)));

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new ServletHolder(
                        new TServlet(new GreeterService.Processor<>(new Handler()), TCompactProtocol::new)),
                "/thrift"
        );
        jettyServer.setHandler(servletContextHandler);
        jettyServer.start();
    }

    @Override
    public void close() {

    }
}

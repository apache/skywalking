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

package org.apache.skywalking.apm.collector.jetty.manager.service;

import java.util.Map;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.server.jetty.JettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JettyManagerServiceImpl implements JettyManagerService {

    private static final Logger logger = LoggerFactory.getLogger(JettyManagerServiceImpl.class);

    private final Map<String, JettyServer> servers;

    public JettyManagerServiceImpl(Map<String, JettyServer> servers) {
        this.servers = servers;
    }

    @Override public JettyServer createIfAbsent(String host, int port, String contextPath) {
        String id = host + String.valueOf(port);
        if (servers.containsKey(id)) {
            return servers.get(id);
        } else {
            JettyServer server = new JettyServer(host, port, contextPath);
            try {
                server.initialize();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
            servers.put(id, server);
            return server;
        }
    }

    @Override public void addHandler(String host, int port, JettyHandler serverHandler) {
        String id = host + String.valueOf(port);
        if (servers.containsKey(id)) {
            servers.get(id).addHandler(serverHandler);
        } else {
            throw new UnexpectedException("Please create server before add server handler.");
        }
    }
}

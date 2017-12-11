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


package org.apache.skywalking.apm.collector.grpc.manager.service;

import java.util.Map;
import org.apache.skywalking.apm.collector.server.Server;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCManagerServiceImpl implements GRPCManagerService {

    private final Logger logger = LoggerFactory.getLogger(GRPCManagerServiceImpl.class);

    private final Map<String, GRPCServer> servers;

    public GRPCManagerServiceImpl(Map<String, GRPCServer> servers) {
        this.servers = servers;
    }

    @Override public Server createIfAbsent(String host, int port) {
        String id = host + String.valueOf(port);
        if (servers.containsKey(id)) {
            return servers.get(id);
        } else {
            GRPCServer server = new GRPCServer(host, port);
            try {
                server.initialize();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
            servers.put(id, server);
            return server;
        }
    }
}

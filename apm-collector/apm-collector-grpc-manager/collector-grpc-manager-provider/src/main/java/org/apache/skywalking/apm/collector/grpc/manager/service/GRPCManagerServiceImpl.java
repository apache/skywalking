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

import java.io.File;
import java.util.Map;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCManagerServiceImpl implements GRPCManagerService {

    private static final Logger logger = LoggerFactory.getLogger(GRPCManagerServiceImpl.class);

    private final Map<String, GRPCServer> servers;

    public GRPCManagerServiceImpl(Map<String, GRPCServer> servers) {
        this.servers = servers;
    }

    @Override
    public GRPCServer createIfAbsent(String host, int port) throws ServerCanNotBeCreatedException {
        return createOrChooseServer(host, port, new GRPCServer(host, port));
    }

    @Override
    public GRPCServer createIfAbsent(String host, int port, File certChainFile,
        File privateKeyFile) throws ServerCanNotBeCreatedException {
        return createOrChooseServer(host, port, new GRPCServer(host, port, certChainFile, privateKeyFile));
    }

    private GRPCServer createOrChooseServer(String host, int port,
        GRPCServer newServer) throws ServerCanNotBeCreatedException {
        String id = host + String.valueOf(port);
        GRPCServer existServer = servers.get(id);
        if (existServer != null) {
            if (existServer.isStatusEqual(newServer)) {
                return existServer;
            } else {
                throw new ServerCanNotBeCreatedException("Can't create server with same port but different setting. SSL setting must equal too.");
            }
        } else {
            try {
                newServer.initialize();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
            servers.put(id, newServer);
            return newServer;
        }
    }
}

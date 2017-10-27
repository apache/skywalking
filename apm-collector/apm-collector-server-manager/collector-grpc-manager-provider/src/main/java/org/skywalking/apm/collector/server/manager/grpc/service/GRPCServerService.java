/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.server.manager.grpc.service;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;
import org.skywalking.apm.collector.server.manager.service.GRPCServerConfig;
import org.skywalking.apm.collector.server.manager.service.GRPCServerManagerService;

/**
 * @author peng-yongsheng
 */
public class GRPCServerService implements GRPCServerManagerService {

    private Map<String, GRPCServer> servers = new HashMap<>();

    @Override public Server getElseCreateServer(GRPCServerConfig config) {
        String id = config.getHost() + String.valueOf(config.getPort());
        if (servers.containsKey(id)) {
            return servers.get(id);
        } else {
            GRPCServer server = new GRPCServer(config.getHost(), config.getPort());
            servers.put(id, server);
            return server;
        }
    }
}

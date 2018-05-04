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

package org.apache.skywalking.apm.collector.grpc.manager;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.grpc.manager.service.GRPCManagerService;
import org.apache.skywalking.apm.collector.grpc.manager.service.GRPCManagerServiceImpl;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCManagerProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(GRPCManagerProvider.class);

    private final GRPCManagerConfig config;
    private Map<String, GRPCServer> servers = new HashMap<>();

    public GRPCManagerProvider() {
        super();
        this.config = new GRPCManagerConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return GRPCManagerModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(GRPCManagerService.class, new GRPCManagerServiceImpl(servers));
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
        servers.values().forEach(server -> {
            try {
                server.start();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}

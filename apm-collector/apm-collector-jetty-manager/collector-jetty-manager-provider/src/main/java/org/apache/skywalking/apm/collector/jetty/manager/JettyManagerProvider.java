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

package org.apache.skywalking.apm.collector.jetty.manager;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerServiceImpl;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.jetty.JettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JettyManagerProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(JettyManagerProvider.class);

    private final JettyManagerConfig jettyManagerConfig;
    private Map<String, JettyServer> servers = new HashMap<>();

    public JettyManagerProvider() {
        this.jettyManagerConfig = new JettyManagerConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return JettyManagerModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return jettyManagerConfig;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(JettyManagerService.class, new JettyManagerServiceImpl(servers));
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

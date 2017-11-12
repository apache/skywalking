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

package org.skywalking.apm.collector.naming.jetty.service;

import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.skywalking.apm.collector.server.ServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NamingJettyHandlerRegisterService implements NamingHandlerRegisterService {

    private final Logger logger = LoggerFactory.getLogger(NamingJettyHandlerRegisterService.class);

    private final ModuleManager moduleManager;
    private final String host;
    private final int port;

    public NamingJettyHandlerRegisterService(String host, int port, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.host = host;
        this.port = port;
    }

    @Override public void register(ServerHandler namingHandler) {
        try {
            JettyManagerService managerService = moduleManager.find(JettyManagerModule.NAME).getService(JettyManagerService.class);
            managerService.addHandler(this.host, this.port, namingHandler);
        } catch (ModuleNotFoundException | ServiceNotProvidedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}

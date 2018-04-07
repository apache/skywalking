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

package org.apache.skywalking.apm.collector.naming.jetty.service;

import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.apache.skywalking.apm.collector.server.ServerHandler;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NamingJettyHandlerRegisterService implements NamingHandlerRegisterService {

    private static final Logger logger = LoggerFactory.getLogger(NamingJettyHandlerRegisterService.class);

    private final ModuleManager moduleManager;
    private final String host;
    private final int port;

    public NamingJettyHandlerRegisterService(String host, int port, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.host = host;
        this.port = port;
    }

    @Override
    public void register(ServerHandler namingHandler) {
        if (!(namingHandler instanceof JettyHandler)) {
            throw new IllegalArgumentException("NamingJettyHandlerRegisterService support JettyHandler only.");
        }
        JettyManagerService managerService = moduleManager.find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        managerService.addHandler(this.host, this.port, (JettyHandler)namingHandler);
    }
}

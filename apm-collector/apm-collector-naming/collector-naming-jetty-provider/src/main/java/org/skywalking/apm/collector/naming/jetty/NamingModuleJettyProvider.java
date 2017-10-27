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

package org.skywalking.apm.collector.naming.jetty;

import java.util.Properties;
import org.skywalking.apm.collector.cluster.ClusterModule;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.naming.NamingModule;
import org.skywalking.apm.collector.naming.jetty.handler.AgentGRPCNamingHandler;
import org.skywalking.apm.collector.naming.jetty.handler.AgentJettyNamingHandler;
import org.skywalking.apm.collector.naming.jetty.handler.UIJettyNamingHandler;
import org.skywalking.apm.collector.server.Server;
import org.skywalking.apm.collector.server.manager.ServerManagerModule;
import org.skywalking.apm.collector.server.manager.service.JettyServerConfig;
import org.skywalking.apm.collector.server.manager.service.JettyServerManagerService;

/**
 * @author peng-yongsheng
 */
public class NamingModuleJettyProvider extends ModuleProvider {

    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String CONTEXT_PATH = "context_path";

    @Override public String name() {
        return "Jetty";
    }

    @Override public Class<? extends Module> module() {
        return NamingModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String host = config.getProperty(HOST);
        String port = config.getProperty(PORT);
        String contextPath = config.getProperty(CONTEXT_PATH);
        JettyServerConfig serverConfig = new JettyServerConfig(host, Integer.valueOf(port), contextPath);

        try {
            JettyServerManagerService managerService = getManager().find(ServerManagerModule.NAME).getService(JettyServerManagerService.class);
            Server jettyServer = managerService.getElseCreateServer(serverConfig);
            jettyServer.addHandler(new AgentGRPCNamingHandler());
            jettyServer.addHandler(new AgentJettyNamingHandler());
            jettyServer.addHandler(new UIJettyNamingHandler());

//            ModuleRegistrationGetService registrationGetService = getManager().find(ClusterModule.NAME).getService(ModuleRegistrationGetService.class);
        } catch (ModuleNotFoundException e) {
            throw new ServiceNotProvidedException(e.getMessage());
        }
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {ServerManagerModule.NAME, ClusterModule.NAME};
    }
}

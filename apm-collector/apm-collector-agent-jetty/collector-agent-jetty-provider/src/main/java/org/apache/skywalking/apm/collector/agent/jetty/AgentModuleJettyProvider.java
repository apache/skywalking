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


package org.apache.skywalking.apm.collector.agent.jetty;

import java.util.Properties;
import org.apache.skywalking.apm.collector.agent.jetty.handler.TraceSegmentServletHandler;
import org.apache.skywalking.apm.collector.agent.stream.AgentStreamModule;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.naming.NamingModule;
import org.apache.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.apache.skywalking.apm.collector.server.Server;
import org.apache.skywalking.apm.collector.agent.jetty.handler.ApplicationRegisterServletHandler;
import org.apache.skywalking.apm.collector.agent.jetty.handler.InstanceDiscoveryServletHandler;
import org.apache.skywalking.apm.collector.agent.jetty.handler.ServiceNameDiscoveryServiceHandler;
import org.apache.skywalking.apm.collector.agent.jetty.handler.naming.AgentJettyNamingHandler;
import org.apache.skywalking.apm.collector.agent.jetty.handler.naming.AgentJettyNamingListener;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;

/**
 * @author peng-yongsheng
 */
public class AgentModuleJettyProvider extends ModuleProvider {

    public static final String NAME = "jetty";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String CONTEXT_PATH = "context_path";

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AgentJettyModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        String host = config.getProperty(HOST);
        Integer port = (Integer)config.get(PORT);
        String contextPath = config.getProperty(CONTEXT_PATH);

        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(AgentJettyModule.NAME, this.name(), new AgentModuleJettyRegistration(host, port, contextPath));

        AgentJettyNamingListener namingListener = new AgentJettyNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        NamingHandlerRegisterService namingHandlerRegisterService = getManager().find(NamingModule.NAME).getService(NamingHandlerRegisterService.class);
        namingHandlerRegisterService.register(new AgentJettyNamingHandler(namingListener));

        JettyManagerService managerService = getManager().find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        Server jettyServer = managerService.createIfAbsent(host, port, contextPath);
        addHandlers(jettyServer);
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, NamingModule.NAME, JettyManagerModule.NAME, AgentStreamModule.NAME};
    }

    private void addHandlers(Server jettyServer) {
        jettyServer.addHandler(new TraceSegmentServletHandler(getManager()));
        jettyServer.addHandler(new ApplicationRegisterServletHandler(getManager()));
        jettyServer.addHandler(new InstanceDiscoveryServletHandler(getManager()));
        jettyServer.addHandler(new ServiceNameDiscoveryServiceHandler(getManager()));
    }
}

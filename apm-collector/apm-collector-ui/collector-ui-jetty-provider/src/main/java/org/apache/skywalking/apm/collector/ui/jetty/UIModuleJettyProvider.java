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

package org.apache.skywalking.apm.collector.ui.jetty;

import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.naming.NamingModule;
import org.apache.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.apache.skywalking.apm.collector.server.jetty.JettyServer;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.ui.UIModule;
import org.apache.skywalking.apm.collector.ui.jetty.handler.GraphQLHandler;
import org.apache.skywalking.apm.collector.ui.jetty.handler.naming.UIJettyNamingHandler;
import org.apache.skywalking.apm.collector.ui.jetty.handler.naming.UIJettyNamingListener;

/**
 * @author peng-yongsheng
 */
public class UIModuleJettyProvider extends ModuleProvider {

    public static final String NAME = "jetty";
    private final UIModuleJettyConfig config;

    public UIModuleJettyProvider() {
        super();
        this.config = new UIModuleJettyConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return UIModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() {
    }

    @Override public void start() {
        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(UIModule.NAME, this.name(), new UIModuleJettyRegistration(config.getHost(), config.getPort(), config.getContextPath()));

        UIJettyNamingListener namingListener = new UIJettyNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        NamingHandlerRegisterService namingHandlerRegisterService = getManager().find(NamingModule.NAME).getService(NamingHandlerRegisterService.class);
        namingHandlerRegisterService.register(new UIJettyNamingHandler(namingListener));

        JettyManagerService managerService = getManager().find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        JettyServer jettyServer = managerService.createIfAbsent(config.getHost(), config.getPort(), config.getContextPath());
        addHandlers(jettyServer);
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {ConfigurationModule.NAME, ClusterModule.NAME, JettyManagerModule.NAME, NamingModule.NAME, CacheModule.NAME, StorageModule.NAME};
    }

    private void addHandlers(JettyServer jettyServer) {
        jettyServer.addHandler(new GraphQLHandler(getManager()));
    }
}

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

package org.apache.skywalking.apm.collector.naming.jetty;

import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.naming.NamingModule;
import org.apache.skywalking.apm.collector.naming.jetty.service.NamingJettyHandlerRegisterService;
import org.apache.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;

/**
 * @author peng-yongsheng
 */
public class NamingModuleJettyProvider extends ModuleProvider {

    private final NamingModuleJettyConfig config;

    public NamingModuleJettyProvider() {
        super();
        this.config = new NamingModuleJettyConfig();
    }

    @Override public String name() {
        return "jetty";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return NamingModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(NamingHandlerRegisterService.class, new NamingJettyHandlerRegisterService(config.getHost(), config.getPort(), getManager()));
    }

    @Override public void start() {
        JettyManagerService managerService = getManager().find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        managerService.createIfAbsent(config.getHost(), config.getPort(), config.getContextPath());
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, JettyManagerModule.NAME};
    }
}

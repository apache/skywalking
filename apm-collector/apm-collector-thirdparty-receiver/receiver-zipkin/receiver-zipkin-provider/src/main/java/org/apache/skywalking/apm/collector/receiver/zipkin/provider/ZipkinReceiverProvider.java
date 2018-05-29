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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider;

import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ModuleStartException;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.jetty.manager.JettyManagerModule;
import org.apache.skywalking.apm.collector.jetty.manager.service.JettyManagerService;
import org.apache.skywalking.apm.collector.receiver.zipkin.define.ZipkinReceiverModule;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.handler.SpanJettyHandler;
import org.apache.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author wusheng
 */
public class ZipkinReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";
    private ZipkinReceiverConfig config;

    public ZipkinReceiverProvider() {
        config = new ZipkinReceiverConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends ModuleDefine> module() {
        return ZipkinReceiverModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {

    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        JettyManagerService managerService = getManager().find(JettyManagerModule.NAME).getService(JettyManagerService.class);
        JettyServer jettyServer = managerService.createIfAbsent(config.getHost(), config.getPort(), config.getContextPath());
        addHandlers(jettyServer);
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {JettyManagerModule.NAME};
    }

    private void addHandlers(JettyServer jettyServer) {
        jettyServer.addHandler(new SpanJettyHandler(config));
    }
}

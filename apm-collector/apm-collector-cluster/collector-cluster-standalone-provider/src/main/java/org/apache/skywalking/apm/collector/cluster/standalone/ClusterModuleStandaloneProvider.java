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

package org.apache.skywalking.apm.collector.cluster.standalone;

import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.cluster.standalone.service.StandaloneModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.standalone.service.StandaloneModuleRegisterService;
import org.apache.skywalking.apm.collector.core.CollectorException;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ClusterModuleStandaloneProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModuleStandaloneProvider.class);

    private final ClusterModuleStandaloneConfig config;
    private H2Client h2Client;
    private ClusterStandaloneDataMonitor dataMonitor;

    public ClusterModuleStandaloneProvider() {
        super();
        this.config = new ClusterModuleStandaloneConfig();
    }

    @Override public String name() {
        return "standalone";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return ClusterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.dataMonitor = new ClusterStandaloneDataMonitor();
        h2Client = new H2Client(config.getUrl(), config.getUserName(), Const.EMPTY_STRING);
        this.dataMonitor.setClient(h2Client);

        this.registerServiceImplementation(ModuleListenerService.class, new StandaloneModuleListenerService(dataMonitor));
        this.registerServiceImplementation(ModuleRegisterService.class, new StandaloneModuleRegisterService(dataMonitor));
    }

    @Override public void start() {
        try {
            h2Client.initialize();
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() {
        try {
            dataMonitor.start();
        } catch (CollectorException e) {
            throw new UnexpectedException(e.getMessage());
        }
    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}

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

package org.skywalking.apm.collector.cluster.standalone;

import java.util.Properties;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.cluster.ClusterModule;
import org.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.skywalking.apm.collector.cluster.standalone.service.StandaloneModuleListenerService;
import org.skywalking.apm.collector.cluster.standalone.service.StandaloneModuleRegisterService;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.core.util.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ClusterModuleStandaloneProvider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(ClusterModuleStandaloneProvider.class);

    private static final String URL = "url";
    private static final String USER_NAME = "user_name";

    private H2Client h2Client;
    private ClusterStandaloneDataMonitor dataMonitor;

    @Override public String name() {
        return "standalone";
    }

    @Override public Class<? extends Module> module() {
        return ClusterModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.dataMonitor = new ClusterStandaloneDataMonitor();

        final String url = config.getProperty(URL);
        final String userName = config.getProperty(USER_NAME);
        h2Client = new H2Client(url, userName, Const.EMPTY_STRING);
        this.dataMonitor.setClient(h2Client);

        this.registerServiceImplementation(ModuleListenerService.class, new StandaloneModuleListenerService(dataMonitor));
        this.registerServiceImplementation(ModuleRegisterService.class, new StandaloneModuleRegisterService(dataMonitor));
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        try {
            h2Client.initialize();
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}

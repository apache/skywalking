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
package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author litian33@gmail.com
 */
public class ClusterModuleNacosProvider extends ModuleProvider {
    private static final Logger logger = LoggerFactory.getLogger(ClusterModuleNacosProvider.class);

    private final ClusterModuleNacosConfig config;
    private NamingService client;

    public ClusterModuleNacosProvider() {
        super();
        this.config = new ClusterModuleNacosConfig();
    }

    @Override
    public String name() {
        return "nacos";
    }

    @Override
    public Class module() {
        return ClusterModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            client = NamingFactory.createNamingService(config.getHostPort());
        } catch (NacosException e) {
            logger.error("create naming service error", e);
            throw new ModuleStartException(e.getMessage(), e);
        }


        NacosCoordinator coordinator = new NacosCoordinator(config, client);
        this.registerServiceImplementation(ClusterRegister.class, coordinator);
        this.registerServiceImplementation(ClusterNodesQuery.class, coordinator);
    }

    @Override
    public void start() {
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }
}

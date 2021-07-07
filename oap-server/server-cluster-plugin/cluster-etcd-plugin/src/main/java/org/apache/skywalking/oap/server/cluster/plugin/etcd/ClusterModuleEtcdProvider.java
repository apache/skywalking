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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * etcd Provider.
 */
public class ClusterModuleEtcdProvider extends ModuleProvider {

    private final ClusterModuleEtcdConfig config;

    public ClusterModuleEtcdProvider() {
        this.config = new ClusterModuleEtcdConfig();
    }

    @Override
    public String name() {
        return "etcd";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ClusterModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            EtcdCoordinator coordinator = new EtcdCoordinator(getManager(), config);
            this.registerServiceImplementation(ClusterRegister.class, coordinator);
            this.registerServiceImplementation(ClusterNodesQuery.class, coordinator);
        } catch (Exception e) {
            throw new ModuleStartException("Failed to start ETCD coordinator.", e);
        }
    }

    @Override
    public void start() throws ServiceNotProvidedException {
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}

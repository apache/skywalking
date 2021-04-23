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

package org.apache.skywalking.oap.server.receiver.zabbix.provider;

import com.google.common.base.Splitter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.receiver.zabbix.module.ZabbixReceiverModule;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.config.ZabbixConfig;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.config.ZabbixConfigs;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.ZabbixServer;

import java.util.Collections;
import java.util.List;

public class ZabbixReceiverProvider extends ModuleProvider {
    private ZabbixModuleConfig moduleConfig;
    private List<ZabbixConfig> configs;
    private ZabbixMetrics zabbixMetrics;

    public ZabbixReceiverProvider() {
        this.moduleConfig = new ZabbixModuleConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ZabbixReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        configs = ZabbixConfigs.loadConfigs(ZabbixModuleConfig.CONFIG_PATH,
            StringUtil.isEmpty(moduleConfig.getActiveFiles()) ? Collections.emptyList() : Splitter.on(",").splitToList(moduleConfig.getActiveFiles()));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (CollectionUtils.isNotEmpty(configs)) {
            // Init metrics
            zabbixMetrics = new ZabbixMetrics(configs, getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class));

            // Bind receiver server
            ZabbixServer zabbixServer = new ZabbixServer(moduleConfig, zabbixMetrics);
            try {
                zabbixServer.start();
            } catch (Exception e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME
        };
    }
}

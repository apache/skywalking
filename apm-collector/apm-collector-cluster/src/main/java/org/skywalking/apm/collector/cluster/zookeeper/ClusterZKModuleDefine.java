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

package org.skywalking.apm.collector.cluster.zookeeper;

import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;

/**
 * @author peng-yongsheng
 */
public class ClusterZKModuleDefine extends ClusterModuleDefine {

    public static final String MODULE_NAME = "zookeeper";
    private final ClusterZKDataMonitor dataMonitor;

    public ClusterZKModuleDefine() {
        dataMonitor = new ClusterZKDataMonitor();
    }

    @Override protected String group() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterZKConfigParser();
    }

    @Override public DataMonitor dataMonitor() {
        return dataMonitor;
    }

    @Override protected Client createClient() {
        return new ZookeeperClient(ClusterZKConfig.HOST_PORT, ClusterZKConfig.SESSION_TIMEOUT, dataMonitor);
    }

    @Override public ClusterModuleRegistrationReader registrationReader() {
        return new ClusterZKModuleRegistrationReader(dataMonitor);
    }
}

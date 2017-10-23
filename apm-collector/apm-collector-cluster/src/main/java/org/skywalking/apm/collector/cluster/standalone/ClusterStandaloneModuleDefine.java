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

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;

/**
 * @author peng-yongsheng
 */
public class ClusterStandaloneModuleDefine extends ClusterModuleDefine {

    public static final String MODULE_NAME = "standalone";

    private final ClusterStandaloneDataMonitor dataMonitor;

    public ClusterStandaloneModuleDefine() {
        this.dataMonitor = new ClusterStandaloneDataMonitor();
    }

    @Override public String group() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterStandaloneConfigParser();
    }

    @Override public DataMonitor dataMonitor() {
        return dataMonitor;
    }

    @Override protected Client createClient() {
        return new H2Client();
    }

    @Override public ClusterModuleRegistrationReader registrationReader() {
        return new ClusterStandaloneModuleRegistrationReader(dataMonitor);
    }
}

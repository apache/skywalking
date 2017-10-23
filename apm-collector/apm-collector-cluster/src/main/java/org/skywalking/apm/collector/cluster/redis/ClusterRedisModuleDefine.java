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

package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.client.redis.RedisClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;

/**
 * @author peng-yongsheng
 */
public class ClusterRedisModuleDefine extends ClusterModuleDefine {

    public static final String MODULE_NAME = "redis";

    @Override public String group() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterRedisConfigParser();
    }

    @Override public DataMonitor dataMonitor() {
        return null;
    }

    @Override protected Client createClient() {
        return new RedisClient(ClusterRedisConfig.HOST, ClusterRedisConfig.PORT);
    }

    @Override public ClusterModuleRegistrationReader registrationReader() {
        return null;
    }
}

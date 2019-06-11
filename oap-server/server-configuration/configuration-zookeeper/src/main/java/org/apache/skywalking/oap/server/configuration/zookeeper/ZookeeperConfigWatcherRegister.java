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

package org.apache.skywalking.oap.server.configuration.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;

import java.util.Set;

/**
 * @author zhaoyuguang
 */
public class ZookeeperConfigWatcherRegister extends ConfigWatcherRegister {
    private final PathChildrenCache childrenCache;

    public ZookeeperConfigWatcherRegister(ZookeeperServerSettings settings) throws Exception {
        super(settings.getPeriod());
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(settings.getBaseSleepTimeMs(), settings.getMaxRetries());
        CuratorFramework client = CuratorFrameworkFactory.newClient(settings.getHostPort(), retryPolicy);
        client.start();
        childrenCache = new PathChildrenCache(client, settings.getNameSpace(), true);
        childrenCache.start();
    }

    @Override
    public ConfigTable readConfig(Set<String> keys) {
        ConfigTable table = new ConfigTable();
        childrenCache.getCurrentData().forEach(e -> table.add(new ConfigTable.ConfigItem(e.getPath(), new String(e.getData()))));
        return table;
    }
}

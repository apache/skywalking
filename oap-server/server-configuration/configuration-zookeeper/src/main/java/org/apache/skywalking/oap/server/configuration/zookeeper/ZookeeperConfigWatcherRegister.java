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

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.FetchingConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;

@Slf4j
public class ZookeeperConfigWatcherRegister extends FetchingConfigWatcherRegister {
    private final CuratorFramework client;
    private final PathChildrenCache childrenCache;
    private final String prefix;

    public ZookeeperConfigWatcherRegister(ZookeeperServerSettings settings) throws Exception {
        super(settings.getPeriod());
        prefix = settings.getNamespace() + "/";
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(settings.getBaseSleepTimeMs(), settings.getMaxRetries());
        this.client = CuratorFrameworkFactory.newClient(settings.getHostPort(), retryPolicy);
        client.start();
        this.childrenCache = new PathChildrenCache(client, settings.getNamespace(), true);
        this.childrenCache.start();
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        ConfigTable table = new ConfigTable();
        keys.forEach(s -> {
            ChildData data = this.childrenCache.getCurrentData(this.prefix + s);
            String itemValue = null;
            if (data != null && data.getData() != null) {
                itemValue = new String(data.getData(), StandardCharsets.UTF_8);
            }
            table.add(new ConfigTable.ConfigItem(s, itemValue));
        });
        return Optional.of(table);
    }

    @Override
    public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
        GroupConfigTable table = new GroupConfigTable();
        keys.forEach(key -> {
            GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(key);
            try {
                client.getChildren().forPath(this.prefix + key).forEach(itemName -> {
                    byte[] data = null;
                    try {
                        data = client.getData().forPath(this.prefix + key + "/" + itemName);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    groupConfigItems.add(
                        new ConfigTable.ConfigItem(
                            itemName, data == null ? null : new String(data, StandardCharsets.UTF_8)));
                });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            table.addGroupConfigItems(groupConfigItems);
        });
        return Optional.of(table);
    }
}

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

package org.apache.skywalking.oap.server.configuration.apollo;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.google.common.base.Strings;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ListeningConfigWatcherRegister;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApolloConfigWatcherRegister extends ListeningConfigWatcherRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApolloConfigWatcherRegister.class);

    private final Config configReader;

    public ApolloConfigWatcherRegister(ApolloConfigurationCenterSettings settings) {
        final String namespace = settings.getNamespace();

        final boolean isDefaultNamespace = Strings.isNullOrEmpty(namespace);

        if (isDefaultNamespace) {
            this.configReader = ConfigService.getAppConfig();
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Read dynamic configs from Apollo default namespace");
            }
        } else {
            this.configReader = ConfigService.getConfig(namespace);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Read dynamic configs from Apollo namespace: {}", namespace);
            }
        }
    }

    @Override
    protected void startListening(final WatcherHolder holder, ConfigChangeCallback configChangeCallback) {
        String key = holder.getKey();
        switch (holder.getWatcher().getWatchType()) {
            case SINGLE:
                // read initial value before listening
                String value = this.configReader.getProperty(key, null);
                if (value != null) {
                    configChangeCallback.onSingleValueChanged(holder, new ConfigTable.ConfigItem(key, value));
                }

                // add change listener
                this.configReader.addChangeListener(changeEvent -> {
                    changeEvent.changedKeys().stream()
                               .filter(changedKey -> Objects.equals(changedKey, key))
                               .findFirst()
                               .ifPresent(changedKey -> {
                                   String newValue = changeEvent.getChange(changedKey).getNewValue();
                                   configChangeCallback.onSingleValueChanged(
                                       holder, new ConfigTable.ConfigItem(changedKey, newValue)
                                   );
                               });
                }, Collections.singleton(key));
                break;
            case GROUP:
                String groupPrefix = key + ".";

                // read initial group value before listening
                Set<String> allKeys = this.configReader.getPropertyNames();
                if (CollectionUtils.isNotEmpty(allKeys)) {
                    GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(key);

                    allKeys.stream().filter(it -> it.startsWith(groupPrefix)).forEach(groupItemKey -> {
                        String itemName = groupItemKey.substring(groupPrefix.length());
                        String itemValue = this.configReader.getProperty(groupItemKey, null);
                        groupConfigItems.add(new ConfigTable.ConfigItem(itemName, itemValue));
                    });

                    configChangeCallback.onGroupValuesChanged(holder, groupConfigItems);
                }

                // add change listener
                this.configReader.addChangeListener(changeEvent -> {
                    GroupConfigTable.GroupConfigItems newGroupConfigItems = new GroupConfigTable.GroupConfigItems(key);

                    for (final String groupItemKey : changeEvent.changedKeys()) {
                        String itemName = groupItemKey.substring(groupPrefix.length());
                        String itemValue = changeEvent.getChange(groupItemKey).getNewValue();
                        newGroupConfigItems.add(new ConfigTable.ConfigItem(itemName, itemValue));
                    }

                    configChangeCallback.onGroupValuesChanged(holder, newGroupConfigItems);
                }, Collections.emptySet(), Collections.singleton(key));
                break;
            default:
                throw new IllegalArgumentException("unsupported watcher type.");
        }
    }
}

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

package org.apache.skywalking.oap.server.configuration.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * @author kezhenxu94
 */
public class NacosConfigWatcherRegister extends ConfigWatcherRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosConfigWatcherRegister.class);

    private final NacosServerSettings settings;
    private final ConfigService configService;
    private final Map<String, String> cachedConfigs = new ConcurrentHashMap<>();

    public NacosConfigWatcherRegister(NacosServerSettings settings) throws NacosException {
        super(settings.getPeriod());

        this.settings = settings;

        final int port = this.settings.getPort();
        final String serverAddr = this.settings.getServerAddr();

        final Properties properties = new Properties();
        properties.put("serverAddr", serverAddr + ":" + port);
        this.configService = NacosFactory.createConfigService(properties);

        final String group = settings.getGroup();
        for (final String dataId : settings.getDataIds()) {
            this.configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    onDataIdValueChanged(dataId, configInfo);
                }
            });
        }
    }

    void onDataIdValueChanged(String dataId, String configInfo) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Nacos config changed: {}: {}", dataId, configInfo);
        }
        if (configInfo == null) {
            cachedConfigs.remove(dataId);
        } else {
            cachedConfigs.put(dataId, configInfo);
        }
    }

    @Override
    public ConfigTable readConfig() {
        final ConfigTable table = new ConfigTable();
        for (final String key : settings.getDataIds()) {
            final String val = cachedConfigs.get(key);
            table.add(new ConfigTable.ConfigItem(key, val));
        }
        return table;
    }
}

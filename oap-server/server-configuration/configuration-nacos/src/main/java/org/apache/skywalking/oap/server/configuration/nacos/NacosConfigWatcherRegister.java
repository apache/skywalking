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
import com.alibaba.nacos.api.exception.NacosException;
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

    private final RemoteEndpointSettings settings;
    private final ConfigService configService;

    public NacosConfigWatcherRegister(RemoteEndpointSettings settings) throws NacosException {
        super(settings.getPeriod());

        this.settings = settings;

        final int port = this.settings.getPort();
        final String serverAddr = this.settings.getServerAddr();

        final Properties properties = new Properties();
        properties.put("serverAddr", serverAddr + ":" + port);
        this.configService = NacosFactory.createConfigService(properties);
    }

    @Override
    public ConfigTable readConfig() {
        final ConfigTable table = new ConfigTable();
        try {
            final String group = settings.getGroup();
            final long timeOutInMs = settings.getTimeOutInMs();
            for (final String dataId : settings.getDataIds()) {
                final String key = dataId;
                final String value = configService.getConfig(dataId, group, timeOutInMs);
                table.add(new ConfigTable.ConfigItem(key, value));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch configurations from Nacos server: {}", this.settings, e);
        }
        return table;
    }
}

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

import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.base.Strings;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.configuration.api.AbstractConfigurationProvider;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Get configuration from Nacos.
 */
public class NacosConfigurationProvider extends AbstractConfigurationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosConfigurationProvider.class);

    private NacosServerSettings settings;

    public NacosConfigurationProvider() {
        settings = new NacosServerSettings();
    }

    @Override
    public String name() {
        return "nacos";
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return settings;
    }

    @Override
    protected ConfigWatcherRegister initConfigReader() throws ModuleStartException {
        LOGGER.info("settings: {}", settings);
        if (Strings.isNullOrEmpty(settings.getServerAddr())) {
            throw new ModuleStartException("Nacos serverAddr cannot be null or empty.");
        }
        if (settings.getPort() <= 0) {
            throw new ModuleStartException("Nacos port must be positive integer.");
        }
        if (Strings.isNullOrEmpty(settings.getGroup())) {
            throw new ModuleStartException("Nacos group cannot be null or empty.");
        }
        if (StringUtil.isNotEmpty(settings.getUsername()) && StringUtil.isNotEmpty(settings.getAccessKey())) {
            throw new ModuleStartException("Nacos Auth method should choose either username or accessKey, not both");
        }
        try {
            return new NacosConfigWatcherRegister(settings);
        } catch (NacosException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }
}

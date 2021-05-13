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

package org.apache.skywalking.oap.server.configuration.etcd;

import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.configuration.api.AbstractConfigurationProvider;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Get Configuration from etcd.
 */
public class EtcdConfigurationProvider extends AbstractConfigurationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdConfigurationProvider.class);

    private EtcdServerSettings settings;

    public EtcdConfigurationProvider() {
        settings = new EtcdServerSettings();
    }

    @Override
    protected ConfigWatcherRegister initConfigReader() throws ModuleStartException {
        LOGGER.info("settings: {}", settings);
        if (Strings.isNullOrEmpty(settings.getServerAddr())) {
            throw new ModuleStartException("Etcd serverAddr cannot be null or empty.");
        }
        if (Strings.isNullOrEmpty(settings.getGroup())) {
            throw new ModuleStartException("Etcd group cannot be null or empty.");
        }

        try {
            return new EtcdConfigWatcherRegister(settings);
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "etcd";
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return settings;
    }
}

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

package org.apache.skywalking.oap.server.configuration.consul;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.AbstractConfigurationProvider;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

/**
 * Get configuration from Consul.
 */
@Slf4j
public class ConsulConfigurationProvider extends AbstractConfigurationProvider {
    private ConsulConfigurationCenterSettings settings;

    @Override
    public String name() {
        return "consul";
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<ConsulConfigurationCenterSettings>() {
            @Override
            public Class type() {
                return ConsulConfigurationCenterSettings.class;
            }

            @Override
            public void onInitialized(final ConsulConfigurationCenterSettings initialized) {
                settings = initialized;
            }
        };
    }

    @Override
    protected ConfigWatcherRegister initConfigReader() throws ModuleStartException {
        log.info("consul settings: {}", settings);

        if (Strings.isNullOrEmpty(settings.getHostAndPorts())) {
            throw new ModuleStartException("Consul hostAndPorts cannot be null or empty");
        }

        return new ConsulConfigurationWatcherRegister(settings);
    }
}

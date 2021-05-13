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
import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApolloConfigWatcherRegister extends ConfigWatcherRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApolloConfigWatcherRegister.class);

    private final Config configReader;

    public ApolloConfigWatcherRegister(ApolloConfigurationCenterSettings settings) {
        super(settings.getPeriod());

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
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        final ConfigTable configTable = new ConfigTable();

        for (final String name : keys) {
            final String value = configReader.getProperty(name, null);
            configTable.add(new ConfigTable.ConfigItem(name, value));
        }

        return Optional.of(configTable);
    }
}

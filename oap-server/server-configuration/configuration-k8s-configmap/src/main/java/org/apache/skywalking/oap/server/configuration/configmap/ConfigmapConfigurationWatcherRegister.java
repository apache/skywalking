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

package org.apache.skywalking.oap.server.configuration.configmap;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;

@Slf4j
public class ConfigmapConfigurationWatcherRegister extends ConfigWatcherRegister {

    private final ConfigurationConfigmapInformer informer;

    public ConfigmapConfigurationWatcherRegister(ConfigmapConfigurationSettings settings,
                                                 ConfigurationConfigmapInformer informer) {
        super(settings.getPeriod());
        this.informer = informer;
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        final ConfigTable configTable = new ConfigTable();
        Optional<V1ConfigMap> v1ConfigMap = informer.configMap();
        for (final String name : keys) {
            final String value = v1ConfigMap.map(V1ConfigMap::getData).map(data -> data.get(name)).orElse(null);
            if (log.isDebugEnabled()) {
                log.debug("read config: name:{} ,value:{}", name, value);
            }
            if (Objects.nonNull(value)) {
                configTable.add(new ConfigTable.ConfigItem(name, value));
            }
        }
        return Optional.of(configTable);
    }

}

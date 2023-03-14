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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.logging.log4j.util.Strings;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Dynamic configuration items, save the dynamic configuration of the agent corresponding to the service.
 */
@Setter
@Getter
@ToString
public class AgentConfigurations {
    private String service;
    private Map<String, String> configuration;
    /**
     * The uuid is calculated by the dynamic configuration of the service.
     */
    private volatile String uuid;

    public AgentConfigurations(final String service, final Map<String, String> configuration, final String uuid) {
        this.service = service;
        this.configuration = configuration;
        this.uuid = uuid;
    }

    public String buildAgentConfigurationsUuid(AgentConfigurations config) {
        if (null != config && !config.getConfiguration().isEmpty()) {
            StringBuilder serviceConfigStr = new StringBuilder();
            buildConfiguration(config)
                    .forEachRemaining(entry -> serviceConfigStr.append(entry.getKey()).append(":").append(entry.getValue()));
            String configStr = serviceConfigStr.toString();
            return Strings.isEmpty(configStr) ? Hashing.sha512().hashString(
                    "EMPTY", StandardCharsets.UTF_8).toString() : Hashing.sha512().hashString(
                    serviceConfigStr.toString(), StandardCharsets.UTF_8).toString();
        }
        return this.uuid;
    }

    public Iterator<Map.Entry<String, String>> buildConfiguration(AgentConfigurations config) {
        if (null != config && !config.getConfiguration().isEmpty()) {
            return Stream.concat(this.configuration.entrySet().stream(),
                    config.getConfiguration().entrySet().stream()
                            .filter(entry -> !configuration.containsKey(entry.getKey()))).iterator();
        }
        return this.configuration.entrySet().iterator();
    }
}

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

package org.apache.skywalking.apm.agent.core.conf;

import lombok.Data;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.apm.util.StringUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 *
 * @author wusheng
 */
public class SnifferConfigInitializer {
    private static final ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);
    private static final String SPECIFIED_CONFIG_PATH = "skywalking_config";
    private static final String DEFAULT_CONFIG_FILE_NAME = "/config/agent.config";
    private static final String ENV_KEY_PREFIX = "skywalking.";
    private static boolean IS_INIT_COMPLETED = false;

    /**
     * If the specified agent config path is set, the agent will try to locate the specified agent config. If the
     * specified agent config path is not set , the agent will try to locate `agent.config`, which should be in the
     * /config directory of agent package.
     * <p>
     * Also try to override the config by system.properties. All the keys in this place should
     * start with {@link #ENV_KEY_PREFIX}. e.g. in env `skywalking.agent.service_name=yourAppName` to override
     * `agent.service_name` in config file.
     * <p>
     * At the end, `agent.service_name` and `collector.servers` must not be blank.
     */
    public static void initialize(String agentOptions) {

        try (InputStreamReader configFileStream = loadConfig()) {
            Properties properties = new Properties();
            properties.load(configFileStream);
            properties.forEach((key, value) -> {
                if (value instanceof String) {
                    properties.replace(key, PropertyPlaceholderHelper.INSTANCE.replacePlaceholders((String)value, properties));
                }
            });
            ConfigInitializer.initialize(properties, Config.class);
        } catch (Exception e) {
            logger.error(e, "Failed to read the config file, skywalking is going to run in default config.");
        }

        try {
            overrideConfigBySystemProp();
        } catch (Exception e) {
            logger.error(e, "Failed to read the system properties.");
        }

        if (StringUtil.isNotBlank(agentOptions)) {
            try {
                agentOptions = agentOptions.trim();
                logger.info("Agent options is {}.", agentOptions);

                overrideConfigByAgentOptions(agentOptions);
            } catch (Exception e) {
                logger.error(e, "Failed to parse the agent options, val is {}.", agentOptions);
            }
        }

        if (StringUtil.isBlank(Config.Agent.SERVICE_NAME)) {
            throw new ExceptionInInitializerError("`agent.service_name` is missing.");
        }
        if (StringUtil.isBlank(Config.Collector.BACKEND_SERVICE)) {
            throw new ExceptionInInitializerError("`collector.backend_service` is missing.");
        }
        if (Config.Plugin.PEER_MAX_LENGTH <= 3) {
            logger.warn("PEER_MAX_LENGTH configuration:{} error, the default value of 200 will be used.", Config.Plugin.PEER_MAX_LENGTH);
            Config.Plugin.PEER_MAX_LENGTH = 200;
        }

        IS_INIT_COMPLETED = true;
    }

    private static void overrideConfigByAgentOptions(String agentOptions) throws IllegalAccessException {
        Properties properties = new Properties();

        // Parse agent options
        Set<Option> options = parseAgentOptions(agentOptions);
        options.forEach(option -> {
            if (StringUtil.isBlank(option.getName()) || StringUtil.isBlank(option.getValue())) {
                // Skip empty option
                return;
            }
            // Put option into properties
            properties.put(option.getName(), option.getValue());
        });

        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, Config.class);
        }
    }

    /**
     * Parse agent options as a set of option
     *
     * @param agentOptions agent options string
     * @return a set of option or empty set
     */
    private static Set<Option> parseAgentOptions(String agentOptions) {
        if (StringUtil.isBlank(agentOptions)) {
            return Collections.emptySet();
        }

        Set<Option> options = new LinkedHashSet<>(2);

        boolean isInQuotes = false;
        StringBuilder currentTerm = new StringBuilder();
        Option currentOption = new Option();

        for (char c : agentOptions.toCharArray()) {
            if (c == '\'' || c == '"') {
                isInQuotes = !isInQuotes;
            } else if (c == '=' && !isInQuotes) {
                // Key-value pair uses '=' as separator
                currentOption.setName(currentTerm.toString());
                currentTerm = new StringBuilder();
            } else if (c == ',' && !isInQuotes) {
                // Multiple options use ',' as separator
                currentOption.setValue(currentTerm.toString());
                currentTerm = new StringBuilder();

                options.add(currentOption);
                currentOption = new Option();
            } else {
                currentTerm.append(c);
            }
        }

        // Add the value for last option
        currentOption.setValue(currentTerm.toString());
        options.add(currentOption);
        return options;
    }

    public static boolean isInitCompleted() {
        return IS_INIT_COMPLETED;
    }

    /**
     * Override the config by system properties. The property key must start with `skywalking`, the result should be as same
     * as in `agent.config`
     * <p>
     * such as: Property key of `agent.service_name` should be `skywalking.agent.service_name`
     *
     */
    private static void overrideConfigBySystemProp() throws IllegalAccessException {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();

        systemProperties.forEach((key, value) -> {
            if (key.toString().startsWith(ENV_KEY_PREFIX)) {
                String realKey = key.toString().substring(ENV_KEY_PREFIX.length());
                properties.put(realKey, value);
            }
        });

        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, Config.class);
        }
    }

    /**
     * Load the specified config file or default config file
     *
     * @return the config file {@link InputStream}
     */
    private static InputStreamReader loadConfig() throws AgentPackageNotFoundException, ConfigNotFoundException {

        String specifiedConfigPath = System.getProperties().getProperty(SPECIFIED_CONFIG_PATH);
        File configFile = StringUtil.isBlank(specifiedConfigPath) ? new File(AgentPackagePath.getPath(), DEFAULT_CONFIG_FILE_NAME) : new File(specifiedConfigPath);

        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("Config file found in {}.", configFile);

                return new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Failed to load agent.config", e);
            }
        }
        throw new ConfigNotFoundException("Failed to load agent.config.");
    }

    /**
     * Option container.
     *
     * @author johnniang
     */
    @Data
    private static class Option {
        private String name;

        private String value;
    }
}

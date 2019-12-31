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

import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.util.PropertiesUtil;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.StringUtil;

import java.io.InputStreamReader;
import java.util.Properties;

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 *
 * @author wusheng
 */
public class SnifferConfigInitializer {
    private static final ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);
    private static String SPECIFIED_CONFIG_PATH = "skywalking_config";
    private static String DEFAULT_CONFIG_FILE_NAME = "/config/agent.config";
    private static String ENV_KEY_PREFIX = "skywalking.";
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
    public static void initializeAgentConfig(String agentOptions) {
        InputStreamReader configFileStream;
        try {
            String specifiedConfigPath = System.getProperties().getProperty(SPECIFIED_CONFIG_PATH);
            String configFile = StringUtil.isEmpty(specifiedConfigPath) ? AgentPackagePath.getPath() + DEFAULT_CONFIG_FILE_NAME : specifiedConfigPath;
            configFileStream = PropertiesUtil.loadConfigFile(configFile);

            Properties properties = PropertiesUtil.Properties(configFileStream);
            PropertiesUtil.initConfigClass(Config.class,properties,ENV_KEY_PREFIX,agentOptions);

            if (StringUtil.isEmpty(Config.Agent.SERVICE_NAME)) {
                throw new ExceptionInInitializerError("`agent.service_name` is missing.");
            }
            if (StringUtil.isEmpty(Config.Collector.BACKEND_SERVICE)) {
                throw new ExceptionInInitializerError("`collector.backend_service` is missing.");
            }
            if (Config.Plugin.PEER_MAX_LENGTH <= 3) {
                logger.warn("PEER_MAX_LENGTH configuration:{} error, the default value of 200 will be used.", Config.Plugin.PEER_MAX_LENGTH);
                Config.Plugin.PEER_MAX_LENGTH = 200;
            }

            IS_INIT_COMPLETED = true;
        } catch (Exception e) {
            logger.error(e, "Failed to read the config file, skywalking is going to run in default config.");
        }
    }

    public static boolean isInitCompleted() {
        return IS_INIT_COMPLETED;
    }

}

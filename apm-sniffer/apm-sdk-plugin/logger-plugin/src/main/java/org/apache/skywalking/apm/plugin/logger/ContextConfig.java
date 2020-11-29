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

package org.apache.skywalking.apm.plugin.logger;

import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

/**
 * contains all config of the logger plugin.
 */
@Getter
public class ContextConfig {

    private final LoggerConfig logbackConfig;
    private final LoggerConfig log4jConfig;
    private final LoggerConfig log4j2Config;

    private ContextConfig(LoggerConfig logbackConfig, LoggerConfig log4jConfig, LoggerConfig log4j2Config) {
        this.logbackConfig = logbackConfig;
        this.log4jConfig = log4jConfig;
        this.log4j2Config = log4j2Config;
    }

    public static ContextConfig getInstance() {
        return HolderContextConfig.INSTANCE;
    }

    //use singleton
    private static class HolderContextConfig {
        private final static ContextConfig INSTANCE = initContextConfig();

        private static ContextConfig initContextConfig() {
            // judge whether has yaml file
            LoggerConfig logbackConfig = null, log4jConfig = null, log4j2Config = null;
            File configFile = null;
            try {
                configFile = new File(AgentPackagePath.getPath(), "/config/logger-plugin/logconfig.properties");
            } catch (AgentPackageNotFoundException e) {
                e.printStackTrace();
            }
            // not has config file, make config default
            if (!configFile.exists()) {
                List<String> packages = new ArrayList<>();
                packages.add("*");
                logbackConfig = new LoggerConfig("logback", packages, LogLevel.ERROR);
                log4jConfig = new LoggerConfig("log4j", packages, LogLevel.ERROR);
                log4j2Config = new LoggerConfig("log4j2", packages, LogLevel.ERROR);
            } else {
                // use config file to init ContextConfig
                try (FileInputStream configFileInputStream = new FileInputStream(configFile)) {
                    List<LoggerConfig> configs = parseConfigFile(configFileInputStream);
                    // initialization of variables which are described in config file
                    for (LoggerConfig loggerConfig : configs) {
                        if ("logback".equals(loggerConfig.getName())) {
                            logbackConfig = fillLoggerConfig(loggerConfig);
                        } else if ("log4j".equals(loggerConfig.getName())) {
                            log4jConfig = fillLoggerConfig(loggerConfig);
                        } else if ("log4j2".equals(loggerConfig.getName())) {
                            log4j2Config = fillLoggerConfig(loggerConfig);
                        } else {
                            throw new IllegalArgumentException();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //creat ContextConfig object
            }
            return new ContextConfig(logbackConfig, log4jConfig, log4j2Config);
        }

        /**
         * @param fileInputStream the input stream of config file
         * @return the list of configuration file analysis result objects
         */
        private static List<LoggerConfig> parseConfigFile(FileInputStream fileInputStream) throws IOException {
            Properties p = new Properties();
            p.load(fileInputStream);
            List<LoggerConfig> configs = new ArrayList<>();
            LoggerConfig logback, log4j, log4j2;
            logback = fillPackageAndLevel(p.getProperty("logback.packages"), p.getProperty("logback.level"));
            log4j = fillPackageAndLevel(p.getProperty("log4j.packages"), p.getProperty("log4j.level"));
            log4j2 = fillPackageAndLevel(p.getProperty("log4j2.packages"), p.getProperty("log4j2.level"));
            if (logback != null) {
                logback.setName("logback");
                configs.add(logback);
            }
            if (log4j != null) {
                log4j.setName("log4j");
                configs.add(log4j);
            }
            if (log4j2 != null) {
                log4j2.setName("log4j2");
                configs.add(log4j2);
            }
            return configs;
        }

        private static LoggerConfig fillPackageAndLevel(String packages, String level) {
            LoggerConfig loggerConfig = null;
            if (packages != null || level != null) {
                loggerConfig = new LoggerConfig();
                if (packages != null) {
                    loggerConfig.setPackages(splitPackages(packages));
                }
                if (level != null) {
                    loggerConfig.setLevel(LogLevel.valueOf(level.toUpperCase()));
                }
            }
            return loggerConfig;
        }

        private static List<String> splitPackages(String packages) {
            return Arrays.asList(packages.split(","));
        }

        /**
         * @param src instance which need to fill
         * @return object that has been filled with default values
         */
        private static LoggerConfig fillLoggerConfig(LoggerConfig src) {
            if (src == null) {
                return null;
            }
            if (src.getLevel() == null) {
                src.setLevel(LogLevel.ERROR);
            }
            if (src.getPackages() == null) {
                List<String> packages = new ArrayList<>();
                packages.add("*");
                src.setPackages(packages);
            }
            return src;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    static class LoggerConfig implements Cloneable {
        private String name;
        private List<String> packages;
        private LogLevel level;

        /**
         * Encapsulate the obtained log information into a map
         *
         * @param loggerName the name of log system,eg:logback
         * @param level      which level of log need to recorder
         * @param args       the params of log
         * @return a message map
         */
        public Map<String, String> toMessageMap(String loggerName, String level, Object... args) {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("log.kind", loggerName);
            messageMap.put("event", level);
            int paramStart = 2;
            // like `warn(String format, Object arg)`
            if (args[0] instanceof String) {
                paramStart = 1;
                messageMap.put("message", args[0].toString());
            } else {
                messageMap.put("message", args[1].toString());
            }
            //build log
            for (int i = paramStart; i < args.length; i++) {
                String key = "param." + (i - paramStart + 1);
                messageMap.put(key, args[i].toString());
            }
            return messageMap;
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        public boolean isLoggable(String name, String level) {
            return LogLevel.valueOf(level.toUpperCase()).priority >= this.level.priority
                    && packages.stream().anyMatch(it -> it.equals("*") || name.startsWith(it));
        }
    }

    enum LogLevel {
        FATAL(50),
        ERROR(40),
        WARN(30),
        INFO(20),
        DEBUG(10),
        TRACE(0);

        private final int priority;

        LogLevel(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }
}

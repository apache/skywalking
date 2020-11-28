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

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * contains all config of the logger plugin.
 */
public class ContextConfig {

    private final LoggerConfig logbackConfig;
    private final LoggerConfig log4jConfig;
    private final LoggerConfig log4j2Config;

    private ContextConfig(LoggerConfig logbackConfig, LoggerConfig log4jConfig, LoggerConfig log4j2Config) {
        this.logbackConfig = logbackConfig;
        this.log4jConfig = log4jConfig;
        this.log4j2Config = log4j2Config;
    }

    public LoggerConfig getLogbackConfig() {
        return logbackConfig;
    }

    public LoggerConfig getLog4jConfig() {
        return log4jConfig;
    }

    public LoggerConfig getLog4j2Config() {
        return log4j2Config;
    }

    public static ContextConfig getInstance() {
        return HolderContextConfig.INSTANCE;
    }

    //use singleton
    private static class HolderContextConfig {
        private final static ContextConfig INSTANCE = initContextConfig();

        private static ContextConfig initContextConfig() {
            // judge whether has yaml file
            File configFile;
            try {
                configFile = new File(AgentPackagePath.getPath(), "/config/logconfig.yaml");
            } catch (AgentPackageNotFoundException e) {
                throw new RuntimeException(e);
            }
            LoggerConfig logbackConfig = null, log4jConfig = null, log4j2Config = null;
            // not has config file, make config default
            if (!configFile.exists()) {
                List<String> packages = new ArrayList<>();
                packages.add("*");
                logbackConfig = new LoggerConfig("logback", packages, LogLevel.ERROR);
                log4jConfig = new LoggerConfig("log4j", packages, LogLevel.ERROR);
                log4j2Config = new LoggerConfig("log4j2", packages, LogLevel.ERROR);
            } else {
                // use config file to init ContextConfig
                FileInputStream configFileInputStream = null;
                try {
                    configFileInputStream = new FileInputStream(configFile);
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
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    //close input stream
                    if (configFileInputStream != null) {
                        try {
                            configFileInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //creat ContextConfig object
            }
            return new ContextConfig(logbackConfig, log4jConfig, log4j2Config);
        }

        /**
         * @param fileInputStream the input stream of config file
         * @return the list of configuration file analysis result objects
         */
        private static List<LoggerConfig> parseConfigFile(FileInputStream fileInputStream) {
            Yaml yaml = new Yaml();
            List<LoggerConfig> configs = new ArrayList<>();
            for (Object o : (List) yaml.loadAll(fileInputStream).iterator().next()) {
                if (o instanceof Map) {
                    Map configMap = (Map) o;
                    LoggerConfig loggerConfig = new LoggerConfig();
                    loggerConfig.setName((String) configMap.get("name"));

                    if (configMap.get("level") != null) {
                        loggerConfig.setLevel(LogLevel.valueOf(configMap.get("level").toString().toUpperCase()));
                    }
                    if (configMap.get("packages") instanceof List) {
                        loggerConfig.setPackages((List<String>) configMap.get("packages"));
                    }
                    configs.add(loggerConfig);
                } else {
                    return null;
                }
            }
            return configs;
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

    static class LoggerConfig implements Cloneable {
        private String name;
        private List<String> packages;
        private LogLevel level;

        public LoggerConfig(String name, List<String> packages, LogLevel level) {
            this.name = name;
            this.packages = packages;
            this.level = level;
        }

        public LoggerConfig() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getPackages() {
            return packages;
        }

        public void setPackages(List<String> packages) {
            this.packages = packages;
        }

        public LogLevel getLevel() {
            return level;
        }

        public void setLevel(LogLevel level) {
            this.level = level;
        }

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

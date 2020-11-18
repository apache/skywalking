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

package org.apache.skywalking.apm.plugin.logger.logbak;

import ch.qos.logback.classic.Level;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * contains all config of the logger plugin.
 */
public class ContextConfig {
    private LoggerConfig logbakConfig, log4jConfig, log4j2Config;

    private ContextConfig(LoggerConfig logbakConfig, LoggerConfig log4jConfig, LoggerConfig log4j2Config) {
        this.logbakConfig = logbakConfig;
        this.log4jConfig = log4jConfig;
        this.log4j2Config = log4j2Config;
    }

    public LoggerConfig getLogbakConfig() {
        return logbakConfig;
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
            File configFile = new File("../../config/logconfig.yaml");
            LoggerConfig logbakConfig = null, log4jConfig = null, log4j2Config = null;
            // not has config file, make config default
            if (!configFile.exists()) {
                List<String> packages = new ArrayList<>();
                packages.add("*");
                logbakConfig = new LoggerConfig("logbak", packages, Level.ERROR
                        , "%date %level [%thread] %logger{10} [%file:%line] %msg%n", "");
                log4jConfig = new LoggerConfig("log4j", packages, Level.ERROR
                        , "%date %level [%thread] %logger{10} [%file:%line] %msg%n", "");
                log4j2Config = new LoggerConfig("log4j2", packages, Level.ERROR
                        , "%date %level [%thread] %logger{10} [%file:%line] %msg%n", "");
                return new ContextConfig(logbakConfig, log4jConfig, log4j2Config);
            } else {
                // use config file to init ContextConfig
                FileInputStream configFileInputStream = null;
                try {
                    configFileInputStream = new FileInputStream(configFile);
                    List<LoggerConfig> configs = parseConfigFile(configFileInputStream);
                    // initialization of variables which are not described in config file
                    for (LoggerConfig loggerConfig : configs) {
                        if ("*".equals(loggerConfig.getName().trim())) {
                            //clone the loggerConfig
                            logbakConfig = (LoggerConfig) loggerConfig.clone();
                            logbakConfig.setName("logabk");
                            logbakConfig = fillLoggerConfig(logbakConfig);

                            log4jConfig = (LoggerConfig) loggerConfig.clone();
                            log4jConfig.setName("log4j");

                            log4j2Config = (LoggerConfig) loggerConfig.clone();
                            log4j2Config.setName("log4j2");
                            log4j2Config = fillLoggerConfig(log4j2Config);
                        } else if ("logbak".equals(loggerConfig.getName())) {
                            logbakConfig = fillLoggerConfig(loggerConfig);
                        } else if ("log4j".equals(loggerConfig.getName())) {
                            log4jConfig = fillLoggerConfig(loggerConfig);
                        } else if ("log4j2".equals(loggerConfig.getName())) {
                            log4j2Config = fillLoggerConfig(loggerConfig);
                        } else {
                            throw new IllegalArgumentException();
                        }
                    }
                } catch (FileNotFoundException | CloneNotSupportedException e) {
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
                return new ContextConfig(logbakConfig, log4jConfig, log4j2Config);
            }
        }

        /**
         * @param fileInputStream the input stream of config file
         * @return the list of configuration file analysis result objects
         */
        private static List<LoggerConfig> parseConfigFile(FileInputStream fileInputStream) {
            Yaml yaml = new Yaml();
            List<LoggerConfig> configs = new ArrayList<>();
            for (Object o : yaml.loadAll(fileInputStream)) {
                if (o instanceof Map) {
                    Map configMap = (Map) o;
                    LoggerConfig loggerConfig = new LoggerConfig();
                    loggerConfig.setName((String) configMap.get("name"));
                    loggerConfig.setPattern((String) configMap.get("pattern"));
                    loggerConfig.setExpression((String) configMap.get("expression"));

                    if (configMap.get("level") != null) {
                        loggerConfig.setLevel(Level.valueOf((String) configMap.get("level")));
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
                src.setLevel(Level.ERROR);
            }
            if (src.getPackages() == null) {
                List<String> packages = new ArrayList<>();
                packages.add("*");
                src.setPackages(packages);
            }
            if (src.getExpression() == null) {
                src.setExpression("");
            }
            if (src.getPattern() == null) {
                src.setPattern("%date %level [%thread] %logger{10} [%file:%line] %msg%n");
            }
            return src;
        }
    }
}

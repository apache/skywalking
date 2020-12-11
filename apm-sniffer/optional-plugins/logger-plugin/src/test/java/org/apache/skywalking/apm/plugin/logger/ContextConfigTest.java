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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContextConfigTest {
    @Before
    public void setup() throws AgentPackageNotFoundException, IOException {
        String configFilePath = AgentPackagePath.getPath() + "/config/logger-plugin/logconfig.properties";
        String configFileBackPath = configFilePath + ".bak";
        // create configuration file
        File configFile = new File(configFilePath);
        File configBackFile = new File(configFileBackPath);
        //backup logconfig.properties
        if (configFile.exists()) {
            configFile.renameTo(configBackFile);
        } else {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            configFile.createNewFile();
            //write configuration file
            Properties properties = new Properties();
            // write content
            properties.setProperty("logback.level", "error");
            properties.setProperty("logback.packages", "package1,package2");
            properties.setProperty("log4j.level", "debug");
            properties.setProperty("log4j.packages", "*");
            FileWriter writer = new FileWriter(configFilePath);
            properties.store(writer, "Open the bridge for log4j2 and logback.");
            writer.flush();
            writer.close();
        }
    }

    @Test
    public void testTwoLogger() {
        // test
        ContextConfig config = ContextConfig.getLatestConfig();
        ContextConfig.LoggerConfig logbackConfig = config.getLogbackConfig();
        ContextConfig.LoggerConfig log4jConfig = config.getLog4jConfig();
        ContextConfig.LoggerConfig log4j2Config = config.getLog4j2Config();
        //test logback
        assertEquals("logback", logbackConfig.getName());
        assertEquals("package1", logbackConfig.getPackages().get(0));
        assertEquals("package2", logbackConfig.getPackages().get(1));
        assertEquals("ERROR", logbackConfig.getLevel().toString());
        //test log4j
        assertEquals("log4j", log4jConfig.getName());
        assertEquals("*", log4jConfig.getPackages().get(0));
        assertEquals("DEBUG", log4jConfig.getLevel().toString());
        //test log4j2
        assertEquals(log4j2Config, null);
    }

    @After
    public void reset() throws AgentPackageNotFoundException {
        String configFilePath = AgentPackagePath.getPath() + "/config/logger-plugin/logconfig.properties";
        String configFileBackPath = configFilePath + ".bak";
        File configFile = new File(configFilePath);
        File configBackFile = new File(configFileBackPath);
        if (configFile.exists()) {
            configFile.delete();
        }
        if (configBackFile.exists()) {
            configBackFile.renameTo(configFile);
        }
    }
}

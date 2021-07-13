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

package org.apache.skywalking.apm.testcase.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MysqlConfig {
    private static final Logger LOGGER = LogManager.getLogger(MysqlConfig.class);
    private static String URL;
    private static String USER_NAME;
    private static String PASSWORD;

    static {
        InputStream inputStream = MysqlConfig.class.getClassLoader().getResourceAsStream("/jdbc.properties");
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }

        URL = properties.getProperty("mysql.url");
        USER_NAME = properties.getProperty("mysql.username");
        PASSWORD = properties.getProperty("mysql.password");
    }

    public static String getUrl() {
        return URL;
    }

    public static String getUserName() {
        return USER_NAME;
    }

    public static String getPassword() {
        return PASSWORD;
    }
}

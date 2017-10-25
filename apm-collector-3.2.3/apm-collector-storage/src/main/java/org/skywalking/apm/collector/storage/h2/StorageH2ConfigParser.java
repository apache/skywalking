/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.h2;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.config.SystemConfig;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author peng-yongsheng
 */
public class StorageH2ConfigParser implements ModuleConfigParser {
    private static final String URL = "url";
    public static final String USER_NAME = "user_name";
    public static final String PASSWORD = "password";

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(URL))) {
            StorageH2Config.URL = (String)config.get(URL);
        } else {
            StorageH2Config.URL = "jdbc:h2:" + SystemConfig.DATA_PATH + "/h2";
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(USER_NAME))) {
            StorageH2Config.USER_NAME = (String)config.get(USER_NAME);
        } else {
            StorageH2Config.USER_NAME = "sa";
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(PASSWORD))) {
            StorageH2Config.PASSWORD = (String)config.get(PASSWORD);
        }
    }
}
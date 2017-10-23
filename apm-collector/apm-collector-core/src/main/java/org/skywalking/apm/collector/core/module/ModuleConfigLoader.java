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

package org.skywalking.apm.collector.core.module;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigLoader;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author peng-yongsheng
 */
public class ModuleConfigLoader implements ConfigLoader<Map<String, Map>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleConfigLoader.class);

    @Override public Map<String, Map> load() throws DefineException {
        Yaml yaml = new Yaml();
        try {
            try {
                Reader applicationReader = ResourceUtils.read("application.yml");
                return (Map<String, Map>)yaml.load(applicationReader);
            } catch (FileNotFoundException e) {
                logger.info("Could not found application.yml file, use default");
                return (Map<String, Map>)yaml.load(ResourceUtils.read("application-default.yml"));
            }
        } catch (FileNotFoundException e) {
            throw new ModuleDefineException(e.getMessage(), e);
        }
    }
}

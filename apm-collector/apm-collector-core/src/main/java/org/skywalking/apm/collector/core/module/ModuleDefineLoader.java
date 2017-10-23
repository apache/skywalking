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

import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ModuleDefineLoader implements Loader<Map<String, Map<String, ModuleDefine>>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleDefineLoader.class);

    @Override public Map<String, Map<String, ModuleDefine>> load() throws DefineException {
        Map<String, Map<String, ModuleDefine>> moduleDefineMap = new LinkedHashMap<>();

        ModuleDefinitionFile definitionFile = new ModuleDefinitionFile();
        logger.info("module definition file name: {}", definitionFile.fileName());
        DefinitionLoader<ModuleDefine> definitionLoader = DefinitionLoader.load(ModuleDefine.class, definitionFile);
        for (ModuleDefine moduleDefine : definitionLoader) {
            logger.info("loaded module definition class: {}", moduleDefine.getClass().getName());

            String groupName = moduleDefine.group();
            if (!moduleDefineMap.containsKey(groupName)) {
                moduleDefineMap.put(groupName, new LinkedHashMap<>());
            }
            moduleDefineMap.get(groupName).put(moduleDefine.name().toLowerCase(), moduleDefine);
        }
        return moduleDefineMap;
    }
}

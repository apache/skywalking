package org.skywalking.apm.collector.core.module;

import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
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

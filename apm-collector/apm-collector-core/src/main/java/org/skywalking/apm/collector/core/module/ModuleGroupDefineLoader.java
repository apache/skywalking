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
public class ModuleGroupDefineLoader implements Loader<Map<String, ModuleGroupDefine>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleGroupDefineLoader.class);

    @Override public Map<String, ModuleGroupDefine> load() throws DefineException {
        Map<String, ModuleGroupDefine> moduleGroupDefineMap = new LinkedHashMap<>();

        ModuleGroupDefineFile definitionFile = new ModuleGroupDefineFile();
        logger.info("module group definition file name: {}", definitionFile.fileName());
        DefinitionLoader<ModuleGroupDefine> definitionLoader = DefinitionLoader.load(ModuleGroupDefine.class, definitionFile);
        for (ModuleGroupDefine moduleGroupDefine : definitionLoader) {
            logger.info("loaded group module definition class: {}", moduleGroupDefine.getClass().getName());

            String groupName = moduleGroupDefine.name().toLowerCase();
            moduleGroupDefineMap.put(groupName, moduleGroupDefine);
        }
        return moduleGroupDefineMap;
    }
}

package org.skywalking.apm.collector.core.module;

import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(ModuleInstaller.class);

    private final Map<String, ModuleDefine> moduleDefineMap;

    protected ModuleInstaller() {
        moduleDefineMap = new LinkedHashMap<>();
        ModuleDefinitionFile definitionFile = new ModuleDefinitionFile();
        logger.info("definition file name: {}", definitionFile.fileName());
        DefinitionLoader<ModuleDefine> definitionLoader = DefinitionLoader.load(ModuleDefine.class, definitionFile);
        for (ModuleDefine moduleDefine : definitionLoader) {
            logger.info("loaded module class: {}", moduleDefine.getClass().getName());
            moduleDefineMap.put(moduleDefine.getName(), moduleDefine);
        }
    }

    public void install(String moduleName, Map moduleConfig) throws ModuleException {
        Map<String, Map> module = (LinkedHashMap)moduleConfig;
        module.entrySet().forEach(subModuleConfig -> {
            String subMoudleName = moduleName + "." + subModuleConfig.getKey();
            logger.info("install sub module {}", subMoudleName);
            try {
                if (moduleDefineMap.containsKey(subMoudleName)) {
                    moduleDefineMap.get(subMoudleName).initialize(subModuleConfig.getValue());
                } else {
                    logger.error("could not found the module definition, module name: {}", subMoudleName);
                }
            } catch (DefineException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}

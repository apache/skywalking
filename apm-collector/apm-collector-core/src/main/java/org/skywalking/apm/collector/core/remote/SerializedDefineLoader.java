package org.skywalking.apm.collector.core.remote;

import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.module.ModuleGroupDefineLoader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SerializedDefineLoader implements Loader<Map<Integer, SerializedDefine>> {

    private final Logger logger = LoggerFactory.getLogger(ModuleGroupDefineLoader.class);

    @Override public Map<Integer, SerializedDefine> load() throws ConfigException {
        Map<Integer, SerializedDefine> serializedDefineMap = new LinkedHashMap<>();
        SerializedDefinitionFile definitionFile = new SerializedDefinitionFile();
        logger.info("serialized definition file name: {}", definitionFile.fileName());

        DefinitionLoader<SerializedDefine> definitionLoader = DefinitionLoader.load(SerializedDefine.class, definitionFile);

        int id = 1;
        for (SerializedDefine serializedDefine : definitionLoader) {
            logger.info("loaded serialized definition class: {}", serializedDefine.getClass().getName());
            serializedDefineMap.put(id, serializedDefine);
        }
        return serializedDefineMap;
    }
}

package org.skywalking.apm.collector.stream.worker.impl.data;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class DataDefineLoader implements Loader<Map<Integer, DataDefine>> {

    private final Logger logger = LoggerFactory.getLogger(DataDefineLoader.class);

    @Override public Map<Integer, DataDefine> load() throws DefineException {
        Map<Integer, DataDefine> dataDefineMap = new HashMap<>();

        DataDefinitionFile definitionFile = new DataDefinitionFile();
        DefinitionLoader<DataDefine> definitionLoader = DefinitionLoader.load(DataDefine.class, definitionFile);
        for (DataDefine dataDefine : definitionLoader) {
            logger.info("loaded data definition class: {}", dataDefine.getClass().getName());
            dataDefineMap.put(dataDefine.defineId(), dataDefine);
        }
        return dataDefineMap;
    }
}

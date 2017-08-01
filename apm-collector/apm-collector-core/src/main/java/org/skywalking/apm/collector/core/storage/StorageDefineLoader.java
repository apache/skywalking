package org.skywalking.apm.collector.core.storage;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StorageDefineLoader implements Loader<List<TableDefine>> {

    private final Logger logger = LoggerFactory.getLogger(StorageDefineLoader.class);

    @Override public List<TableDefine> load() throws DefineException {
        List<TableDefine> tableDefines = new LinkedList<>();

        StorageDefinitionFile definitionFile = new StorageDefinitionFile();
        logger.info("storage definition file name: {}", definitionFile.fileName());
        DefinitionLoader<TableDefine> definitionLoader = DefinitionLoader.load(TableDefine.class, definitionFile);
        for (TableDefine tableDefine : definitionLoader) {
            logger.info("loaded storage definition class: {}", tableDefine.getClass().getName());
            tableDefines.add(tableDefine);
        }
        return tableDefines;
    }
}

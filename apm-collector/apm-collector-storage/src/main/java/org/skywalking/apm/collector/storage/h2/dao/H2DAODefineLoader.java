package org.skywalking.apm.collector.storage.h2.dao;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class H2DAODefineLoader implements Loader<List<H2DAO>> {

    private final Logger logger = LoggerFactory.getLogger(H2DAODefineLoader.class);

    @Override public List<H2DAO> load() throws DefineException {
        List<H2DAO> h2DAOs = new ArrayList<>();

        H2DAODefinitionFile definitionFile = new H2DAODefinitionFile();
        logger.info("h2 dao definition file name: {}", definitionFile.fileName());
        DefinitionLoader<H2DAO> definitionLoader = DefinitionLoader.load(H2DAO.class, definitionFile);
        for (H2DAO dao : definitionLoader) {
            logger.info("loaded h2 dao definition class: {}", dao.getClass().getName());
            h2DAOs.add(dao);
        }
        return h2DAOs;
    }
}

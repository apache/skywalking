package org.skywalking.apm.collector.worker.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.worker.config.EsConfig;

import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * @author pengys5
 */
public enum IndexCreator {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(IndexCreator.class);

    public void create() {
        if (!EsConfig.IndexInitMode.MANUAL.equals(EsConfig.Es.Index.Initialize.MODE)) {
            Set<AbstractIndex> indexSet = loadIndex();
            for (AbstractIndex index : indexSet) {
                boolean isExists = index.isExists();
                if (isExists) {
                    if (EsConfig.IndexInitMode.FORCED.equals(EsConfig.Es.Index.Initialize.MODE)) {
                        index.deleteIndex();
                        index.createIndex();
                    }
                } else {
                    index.createIndex();
                }
            }
        }
    }

    private Set<AbstractIndex> loadIndex() {
        Set<AbstractIndex> indexSet = new HashSet<>();
        ServiceLoader<AbstractIndex> indexServiceLoader = ServiceLoader.load(AbstractIndex.class);
        for (AbstractIndex index : indexServiceLoader) {
            logger.info("index NAME: %s", index.index());
            indexSet.add(index);
        }
        return indexSet;
    }
}

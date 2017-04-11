package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        if (!EsConfig.Create_Off.equals(EsConfig.Es.Index.create)) {
            Set<AbstractIndex> indexSet = loadIndex();
            for (AbstractIndex index : indexSet) {
                boolean isExists = index.isExists();
                if (isExists) {
                    if (EsConfig.Create_Overwrite.equals(EsConfig.Es.Index.create)) {
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
            logger.info("index name: %s", index.index());
            indexSet.add(index);
        }
        return indexSet;
    }
}

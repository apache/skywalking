package org.skywalking.apm.collector.storage.dao;

import java.util.List;

/**
 * @author pengys5
 */
public interface IBatchDAO {
    void batchPersistence(List<?> batchCollection);
}

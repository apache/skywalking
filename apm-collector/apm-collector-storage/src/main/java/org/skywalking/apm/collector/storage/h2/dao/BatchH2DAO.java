package org.skywalking.apm.collector.storage.h2.dao;

import java.util.List;
import org.skywalking.apm.collector.storage.dao.IBatchDAO;

/**
 * @author pengys5
 */
public class BatchH2DAO extends H2DAO implements IBatchDAO {

    @Override public void batchPersistence(List<?> batchCollection) {

    }
}

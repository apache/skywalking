package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class GCMetricH2DAO extends H2DAO implements IGCMetricDAO {

    @Override public GCCount getGCCount(long timestamp, int instanceId) {
        return null;
    }
}

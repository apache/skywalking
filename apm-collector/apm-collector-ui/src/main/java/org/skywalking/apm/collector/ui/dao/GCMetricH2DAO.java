package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GCMetricH2DAO extends H2DAO implements IGCMetricDAO {
    private final Logger logger = LoggerFactory.getLogger(GCMetricH2DAO.class);
    private static final String GET_GC_COUNT_SQL = "select sum({0}) as cnt, {1} from {2} where {3} > ? group by {1}";
    @Override public GCCount getGCCount(long[] timeBuckets, int instanceId) {
        GCCount gcCount = new GCCount();

        return gcCount;
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket) {
        return null;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        return null;
    }
}

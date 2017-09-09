package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class GCMetricH2DAO extends H2DAO implements IGCMetricDAO {

    @Override public GCCount getGCCount(long[] timeBuckets, int instanceId) {
        return null;
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket) {
        return null;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        return null;
    }
}

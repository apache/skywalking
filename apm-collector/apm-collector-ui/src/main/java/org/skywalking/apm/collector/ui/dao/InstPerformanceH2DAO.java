package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;

/**
 * @author pengys5
 */
public class InstPerformanceH2DAO extends H2DAO implements IInstPerformanceDAO {

    @Override public InstPerformance get(long[] timeBuckets, int instanceId) {
        return null;
    }

    @Override public int getTpsMetric(int instanceId, long timeBucket) {
        return 0;
    }

    @Override public JsonArray getTpsMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        return null;
    }

    @Override public int getRespTimeMetric(int instanceId, long timeBucket) {
        return 0;
    }

    @Override public JsonArray getRespTimeMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        return null;
    }
}

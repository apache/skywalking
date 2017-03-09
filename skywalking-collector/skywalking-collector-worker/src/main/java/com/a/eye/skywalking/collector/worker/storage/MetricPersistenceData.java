package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.tools.PersistenceDataTools;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricPersistenceData extends AbstractHashMessage {

    private Map<String, Map<String, Long>> persistenceData = new HashMap();

    public void setMetric(String id, int second, Long value) {
        if (persistenceData.containsKey(id)) {
            String columnName = PersistenceDataTools.second2ColumnName(second);
            Long metric = persistenceData.get(id).get(columnName);
            persistenceData.get(id).put(columnName, metric + value);
        } else {
            Map<String, Long> metrics = PersistenceDataTools.getFilledPersistenceData();
            metrics.put(PersistenceDataTools.second2ColumnName(second), value);
            persistenceData.put(id, metrics);
        }
    }

    public void setMetric(String id, String column, Long value) {
        if (persistenceData.containsKey(id)) {
            Long metric = persistenceData.get(id).get(column);
            persistenceData.get(id).put(column, metric + value);
        } else {
            Map<String, Long> metrics = PersistenceDataTools.getFilledPersistenceData();
            metrics.put(column, value);
            persistenceData.put(id, metrics);
        }
    }

    public Map<String, Map<String, Long>> getData() {
        return persistenceData;
    }

    public int size() {
        return persistenceData.size();
    }

    public void clear() {
        persistenceData.clear();
    }
}

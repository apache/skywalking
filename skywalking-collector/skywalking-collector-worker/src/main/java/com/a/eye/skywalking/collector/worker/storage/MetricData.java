package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricData extends AbstractHashMessage {

    private String id;
    private Map<String, Long> value;

    public MetricData(String id) {
        super(id);
        this.id = id;
        value = new HashMap<>();
        value.put(AbstractIndex.Time_Slice, Long.valueOf(id.split("-")[0]));
    }

    public void setMetric(String column, Long value) {
        long valueAdd = value;
        if (this.value.containsKey(column) && !AbstractIndex.Time_Slice.equals(column)) {
            valueAdd += this.value.get(column);
        }
        this.value.put(column, valueAdd);
    }

    public void merge(MetricData metricData) {
        for (Map.Entry<String, Long> entry : metricData.value.entrySet()) {
            setMetric(entry.getKey(), entry.getValue());
        }
    }

    public void merge(Map<String, Object> dbData) {
        for (Map.Entry<String, Object> entry : dbData.entrySet()) {
            setMetric(entry.getKey(), (Long) entry.getValue());
        }
    }

    public Map<String, Long> toMap() {
        return value;
    }

    public String getId() {
        return id;
    }
}

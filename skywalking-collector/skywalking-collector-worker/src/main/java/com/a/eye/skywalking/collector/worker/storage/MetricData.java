package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.Const;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricData extends AbstractHashMessage implements Data {

    private String id;
    private Map<String, Object> value;

    public MetricData(String id) {
        super(id);
        this.id = id;
        value = new LinkedHashMap<>();

        String[] ids = id.split(Const.IDS_SPLIT);
        String slice = ids[0];
        StringBuilder aggId = new StringBuilder();
        for (int i = 1; i < ids.length; i++) {
            if (i == 1) aggId = new StringBuilder(ids[i]);
            else aggId.append(Const.ID_SPLIT).append(ids[i]);
        }

        value.put(AbstractIndex.TIME_SLICE, Long.valueOf(slice));
        value.put(AbstractIndex.AGG_COLUMN, aggId.toString());
    }

    public void setMetric(String column, Long value) {
        long valueAdd = value;
        if (this.value.containsKey(column) && !AbstractIndex.TIME_SLICE.equals(column)
                && !AbstractIndex.AGG_COLUMN.equals(column)) {
            valueAdd += (Long) this.value.get(column);
        }
        this.value.put(column, valueAdd);
    }

    public void merge(MetricData metricData) {
        for (Map.Entry<String, Object> entry : metricData.value.entrySet()) {
            if (!AbstractIndex.TIME_SLICE.equals(entry.getKey())
                    && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                setMetric(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    public void merge(Map<String, ?> dbData) {
        for (Map.Entry<String, ?> entry : dbData.entrySet()) {
            if (!AbstractIndex.TIME_SLICE.equals(entry.getKey())
                    && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                long dbValue = ((Number) entry.getValue()).longValue();
                setMetric(entry.getKey(), dbValue);
            }
        }
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> asMap() {
        return value;
    }
}

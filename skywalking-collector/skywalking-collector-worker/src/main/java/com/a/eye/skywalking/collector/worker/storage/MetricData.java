package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.selector.AbstractHashMessage;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.storage.index.AbstractIndex;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricData extends AbstractHashMessage {

    private String id;
    private Map<String, Object> value;

    public MetricData(String id) {
        super(id);
        this.id = id;
        value = new HashMap<>();

        String[] ids = id.split(Const.IDS_SPLIT);
        String slice = ids[0];
        String aggId = "";
        for (int i = 1; i < ids.length; i++) {
            if (i == 1) {
                aggId = ids[i];
            } else {
                aggId = aggId + Const.ID_SPLIT + ids[i];
            }
        }

        value.put(AbstractIndex.Time_Slice, Long.valueOf(slice));
        value.put(AbstractIndex.AGG_COLUMN, aggId);
    }

    public void setMetric(String column, Long value) {
        long valueAdd = value;
        if (this.value.containsKey(column) && !AbstractIndex.Time_Slice.equals(column)
                && !AbstractIndex.AGG_COLUMN.equals(column)) {
            valueAdd += (Long) this.value.get(column);
        }
        this.value.put(column, valueAdd);
    }

    public void merge(MetricData metricData) {
        for (Map.Entry<String, Object> entry : metricData.value.entrySet()) {
            if (!AbstractIndex.Time_Slice.equals(entry.getKey())
                    && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                setMetric(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    public void merge(Map<String, Object> dbData) {
        for (Map.Entry<String, Object> entry : dbData.entrySet()) {
            if (!AbstractIndex.Time_Slice.equals(entry.getKey())
                    && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                long dbValue = ((Number) entry.getValue()).longValue();
                setMetric(entry.getKey(), dbValue);
            }
        }
    }

    public Map<String, Object> toMap() {
        return value;
    }

    public String getId() {
        return id;
    }
}

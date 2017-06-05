package org.skywalking.apm.collector.worker.storage;

import org.skywalking.apm.collector.actor.selector.AbstractHashMessage;
import org.skywalking.apm.collector.worker.Const;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricData extends AbstractHashMessage implements Data {

    private String id;
    private Map<String, Object> data;

    public MetricData(String id) {
        super(id);
        this.id = id;
        data = new LinkedHashMap<>();

        String[] ids = id.split(Const.IDS_SPLIT);
        String slice = ids[0];
        StringBuilder aggId = new StringBuilder();
        for (int i = 1; i < ids.length; i++) {
            if (i == 1) aggId = new StringBuilder(ids[i]);
            else aggId.append(Const.ID_SPLIT).append(ids[i]);
        }

        data.put(AbstractIndex.TIME_SLICE, Long.valueOf(slice));
        data.put(AbstractIndex.AGG_COLUMN, aggId.toString());
    }

    public void set(String metricName, Long value) {
        long valueAdd = value;
        if (this.data.containsKey(metricName) && !AbstractIndex.TIME_SLICE.equals(metricName)
            && !AbstractIndex.AGG_COLUMN.equals(metricName)) {
            valueAdd += (Long) this.data.get(metricName);
        }
        this.data.put(metricName, valueAdd);
    }

    public void merge(MetricData source) {
        for (Map.Entry<String, Object> entry : source.data.entrySet()) {
            if (!AbstractIndex.TIME_SLICE.equals(entry.getKey())
                && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                set(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    @Override
    public void merge(Map<String, ?> source) {
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (!AbstractIndex.TIME_SLICE.equals(entry.getKey())
                && !AbstractIndex.AGG_COLUMN.equals(entry.getKey())) {
                long dbValue = ((Number) entry.getValue()).longValue();
                set(entry.getKey(), dbValue);
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public Map<String, Object> asMap() {
        return data;
    }
}

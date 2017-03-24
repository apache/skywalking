package com.a.eye.skywalking.collector.worker.storage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;

/**
 * @author pengys5
 */
public class MetricPersistenceData implements Iterable {

    private Map<String, MetricData> persistenceData = new HashMap();

    public MetricData getElseCreate(String id) {
        if (!persistenceData.containsKey(id)) {
            persistenceData.put(id, new MetricData(id));
        }
        return persistenceData.get(id);
    }

    public int size() {
        return persistenceData.size();
    }

    public void clear() {
        persistenceData.clear();
    }

    public MetricData pushOne() {
        MetricData one = persistenceData.entrySet().iterator().next().getValue();
        persistenceData.remove(one.getId());
        return one;
    }

    @Override
    public Spliterator spliterator() {
        throw new UnsupportedOperationException("spliterator");
    }

    @Override
    public Iterator<Map.Entry<String, MetricData>> iterator() {
        return persistenceData.entrySet().iterator();
    }
}

package org.skywalking.apm.collector.worker.storage;

/**
 * @author pengys5
 */
public class MetricPersistenceData extends Window<MetricData> implements PersistenceData<MetricData> {

    private WindowData<MetricData> lockedWindowData;

    public MetricData getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new MetricData(id));
        }
        return lockedWindowData.get(id);
    }

    public void hold() {
        lockedWindowData = getCurrentAndHold();
    }

    public void release() {
        lockedWindowData.release();
        lockedWindowData = null;
    }
}

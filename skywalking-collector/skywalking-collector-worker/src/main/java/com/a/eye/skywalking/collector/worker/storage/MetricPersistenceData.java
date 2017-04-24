package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public class MetricPersistenceData extends Window<MetricData> implements PersistenceData<MetricData> {

    private WindowData<MetricData> lockedWindowData;

    public MetricData getElseCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new MetricData(id));
        }
        return lockedWindowData.get(id);
    }

    public void holdData() {
        lockedWindowData = getCurrentAndHold();
    }

    public void releaseData() {
        lockedWindowData.release();
        lockedWindowData = null;
    }
}

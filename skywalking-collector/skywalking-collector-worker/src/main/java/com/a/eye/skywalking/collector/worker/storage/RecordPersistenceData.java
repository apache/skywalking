package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public class RecordPersistenceData extends Window<RecordData> implements PersistenceData<RecordData> {

    private WindowData<RecordData> lockedWindowData;

    public RecordData getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new RecordData(id));
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

package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public class RecordPersistenceData extends Window<RecordData> implements PersistenceData<RecordData> {

    private WindowData<RecordData> lockedWindowData;

    public RecordData getElseCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new RecordData(id));
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

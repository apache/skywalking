package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public class MergePersistenceData extends Window<MergeData> implements PersistenceData<MergeData> {

    private WindowData<MergeData> lockedWindowData;

    public MergeData getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new MergeData(id));
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

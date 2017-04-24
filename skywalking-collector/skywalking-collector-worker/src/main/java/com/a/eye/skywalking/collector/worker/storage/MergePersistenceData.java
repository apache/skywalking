package com.a.eye.skywalking.collector.worker.storage;

/**
 * @author pengys5
 */
public class MergePersistenceData extends Window<MergeData> implements PersistenceData<MergeData> {

    private WindowData<MergeData> lockedWindowData;

    public MergeData getElseCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new MergeData(id));
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

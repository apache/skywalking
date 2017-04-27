package com.a.eye.skywalking.collector.worker.storage;

import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentPersistenceData extends Window<SegmentData> implements PersistenceData<SegmentData> {

    private WindowData<SegmentData> lockedWindowData;

    public SegmentData getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new SegmentData(id));
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

    public int size() {
        return lockedWindowData.size();
    }

    public Map<String, SegmentData> asMap() {
        return lockedWindowData.asMap();
    }
}

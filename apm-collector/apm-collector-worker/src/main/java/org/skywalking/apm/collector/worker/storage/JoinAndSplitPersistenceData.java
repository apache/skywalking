package org.skywalking.apm.collector.worker.storage;

/**
 * @author pengys5
 */
public class JoinAndSplitPersistenceData extends Window<JoinAndSplitData> implements PersistenceData<JoinAndSplitData> {

    private WindowData<JoinAndSplitData> lockedWindowData;

    public JoinAndSplitData getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new JoinAndSplitData(id));
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

package org.skywalking.apm.collector.worker.instance.heartbeat;

import org.skywalking.apm.collector.worker.instance.entity.HeartBeat;
import org.skywalking.apm.collector.worker.storage.PersistenceData;
import org.skywalking.apm.collector.worker.storage.Window;
import org.skywalking.apm.collector.worker.storage.WindowData;

public class HeartBeatPersistenceData extends Window<HeartBeat> implements PersistenceData<HeartBeat> {

    private WindowData<HeartBeat> lockedWindowData;

    @Override
    public HeartBeat getOrCreate(String id) {
        if (!lockedWindowData.containsKey(id)) {
            lockedWindowData.put(id, new HeartBeat(id));
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

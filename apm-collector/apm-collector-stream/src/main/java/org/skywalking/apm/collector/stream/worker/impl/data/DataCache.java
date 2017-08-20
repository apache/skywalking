package org.skywalking.apm.collector.stream.worker.impl.data;

import org.skywalking.apm.collector.core.stream.Data;

/**
 * @author pengys5
 */
public class DataCache extends Window {

    private DataCollection lockedDataCollection;

    public boolean containsKey(String id) {
        return lockedDataCollection.containsKey(id);
    }

    public Data get(String id) {
        return lockedDataCollection.get(id);
    }

    public void put(String id, Data data) {
        lockedDataCollection.put(id, data);
    }

    public void hold() {
        lockedDataCollection = getCurrentAndHold();
    }

    public int currentCollectionSize() {
        return getCurrentAndHold().size();
    }

    public void release() {
        lockedDataCollection.release();
        lockedDataCollection = null;
    }
}

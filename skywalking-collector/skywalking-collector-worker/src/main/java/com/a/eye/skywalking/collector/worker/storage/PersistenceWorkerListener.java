package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorker;
import java.util.LinkedList;
import java.util.List;

/**
 * @author pengys5
 */
public enum PersistenceWorkerListener {
    INSTANCE;

    private List<AbstractLocalSyncWorker> workers = new LinkedList<>();

    public void register(AbstractLocalSyncWorker worker) {
        workers.add(worker);
    }

    public List<AbstractLocalSyncWorker> getWorkers() {
        return workers;
    }

    public void reset() {
        workers.clear();
    }
}

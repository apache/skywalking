package org.skywalking.apm.collector.stream.worker.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public enum PersistenceWorkerContainer {
    INSTANCE;

    private List<PersistenceWorker> persistenceWorkers = new ArrayList<>();

    public void addWorker(PersistenceWorker worker) {
        persistenceWorkers.add(worker);
    }

    public List<PersistenceWorker> getPersistenceWorkers() {
        return persistenceWorkers;
    }
}

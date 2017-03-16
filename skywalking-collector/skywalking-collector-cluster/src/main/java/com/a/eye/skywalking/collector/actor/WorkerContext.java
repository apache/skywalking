package com.a.eye.skywalking.collector.actor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public abstract class WorkerContext implements Context {

    private Map<String, List<WorkerRef>> roleWorkers = new ConcurrentHashMap<>();

    @Override
    final public WorkerRefs lookup(Role role) throws WorkerNotFountException {
        if (roleWorkers.containsKey(role.name())) {
            WorkerRefs refs = new WorkerRefs(roleWorkers.get(role.name()), role.workerSelector());
            return refs;
        } else {
            throw new WorkerNotFountException("role=" + role.name() + ", no available worker.");
        }
    }

    @Override
    final public void put(WorkerRef workerRef) {
        if (!roleWorkers.containsKey(workerRef.getRole().name())) {
            List<WorkerRef> actorList = Collections.synchronizedList(new ArrayList<WorkerRef>());
            roleWorkers.putIfAbsent(workerRef.getRole().name(), actorList);
        }
        roleWorkers.get(workerRef.getRole().name()).add(workerRef);
    }

    @Override
    final public void remove(WorkerRef workerRef) {
        roleWorkers.remove(workerRef);
    }
}

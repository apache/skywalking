package com.a.eye.skywalking.collector.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public abstract class WorkerContext implements Context {

    private Map<String, List<WorkerRef>> roleWorkers = new ConcurrentHashMap<>();

    @Override
    final public WorkerRefs lookup(Role role) throws WorkerNotFoundException {
        if (roleWorkers.containsKey(role.roleName())) {
            WorkerRefs refs = new WorkerRefs(roleWorkers.get(role.roleName()), role.workerSelector());
            return refs;
        } else {
            throw new WorkerNotFoundException("role=" + role.roleName() + ", no available worker.");
        }
    }

    @Override
    final public void put(WorkerRef workerRef) {
        if (!roleWorkers.containsKey(workerRef.getRole().roleName())) {
            roleWorkers.putIfAbsent(workerRef.getRole().roleName(), new ArrayList<WorkerRef>());
        }
        roleWorkers.get(workerRef.getRole().roleName()).add(workerRef);
    }

    @Override
    final public void remove(WorkerRef workerRef) {
        roleWorkers.remove(workerRef.getRole().roleName());
    }
}

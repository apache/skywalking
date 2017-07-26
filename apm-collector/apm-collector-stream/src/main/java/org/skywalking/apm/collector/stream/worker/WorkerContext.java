package org.skywalking.apm.collector.stream.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public abstract class WorkerContext implements Context {

    private Map<String, RemoteWorkerRef> remoteWorkerRefs;
    private Map<String, List<WorkerRef>> roleWorkers;
    private Map<String, Role> roles;
    private Map<Integer, DataDefine> dataDefineMap;

    public WorkerContext() {
        this.roleWorkers = new HashMap<>();
        this.roles = new HashMap<>();
        this.remoteWorkerRefs = new HashMap<>();
    }

    private Map<String, List<WorkerRef>> getRoleWorkers() {
        return this.roleWorkers;
    }

    @Override final public WorkerRefs lookup(Role role) throws WorkerNotFoundException {
        if (getRoleWorkers().containsKey(role.roleName())) {
            WorkerRefs refs = new WorkerRefs(getRoleWorkers().get(role.roleName()), role.workerSelector());
            return refs;
        } else {
            throw new WorkerNotFoundException("role=" + role.roleName() + ", no available worker.");
        }
    }

    @Override final public RemoteWorkerRef lookupInSide(String roleName) throws WorkerNotFoundException {
        if (remoteWorkerRefs.containsKey(roleName)) {
            return remoteWorkerRefs.get(roleName);
        } else {
            throw new WorkerNotFoundException("role=" + roleName + ", no available worker.");
        }
    }

    public final void putRole(Role role) {
        roles.put(role.roleName(), role);
    }

    public final Role getRole(String roleName) {
        return roles.get(roleName);
    }

    public final DataDefine getDataDefine(int defineId) {
        return dataDefineMap.get(defineId);
    }

    @Override final public void put(WorkerRef workerRef) {
        if (!getRoleWorkers().containsKey(workerRef.getRole().roleName())) {
            getRoleWorkers().putIfAbsent(workerRef.getRole().roleName(), new ArrayList<>());
        }
        getRoleWorkers().get(workerRef.getRole().roleName()).add(workerRef);

        if (workerRef instanceof RemoteWorkerRef) {
            RemoteWorkerRef remoteWorkerRef = (RemoteWorkerRef)workerRef;
            if (!remoteWorkerRef.isAcrossJVM()) {
                remoteWorkerRefs.put(workerRef.getRole().roleName(), remoteWorkerRef);
            }
        }
    }

    @Override final public void remove(WorkerRef workerRef) {
        getRoleWorkers().remove(workerRef.getRole().roleName());
    }
}

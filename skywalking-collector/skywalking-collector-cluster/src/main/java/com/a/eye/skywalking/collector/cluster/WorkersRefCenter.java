package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <code>WorkersRefCenter</code> represent a cache center,
 * store all {@link ActorRef}s, each of them represent a Akka Actor instance.
 * All the Actors in this JVM, can find alive-actor in here, and send message.
 *
 * @author wusheng
 */
public enum WorkersRefCenter {
    INSTANCE;

    private Map<String, List<WorkerRef>> roleToWorkerRef = new ConcurrentHashMap();

    private Map<ActorRef, WorkerRef> actorRefToWorkerRef = new ConcurrentHashMap<>();

    public void register(ActorRef newActorRef, String workerRole) {
        if (!roleToWorkerRef.containsKey(workerRole)) {
            List<WorkerRef> actorList = Collections.synchronizedList(new ArrayList<WorkerRef>());
            roleToWorkerRef.putIfAbsent(workerRole, actorList);
        }

        WorkerRef newWorkerRef = new WorkerRef(newActorRef, workerRole);
        roleToWorkerRef.get(workerRole).add(newWorkerRef);
        actorRefToWorkerRef.put(newActorRef, newWorkerRef);
    }

    public void unregister(ActorRef oldActorRef) {
        WorkerRef oldWorkerRef = actorRefToWorkerRef.get(oldActorRef);
        roleToWorkerRef.get(oldWorkerRef.getWorkerRole()).remove(oldWorkerRef);
        actorRefToWorkerRef.remove(oldActorRef);
    }

    /**
     * Get all available {@link WorkerRef} list, by the given worker role.
     *
     * @param workerRole the given role
     * @return available {@link WorkerRef} list
     * @throws NoAvailableWorkerException , when no available worker.
     */
    public List<WorkerRef> availableWorks(String workerRole) throws NoAvailableWorkerException {
        List<WorkerRef> refs = roleToWorkerRef.get(workerRole);
        if (refs == null || refs.size() == 0) {
            throw new NoAvailableWorkerException("role=" + workerRole + ", no available worker.");
        }
        return Collections.unmodifiableList(refs);
    }
}

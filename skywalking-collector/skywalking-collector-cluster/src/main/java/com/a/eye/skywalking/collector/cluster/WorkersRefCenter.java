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

    private Map<String, List<WorkerRef>> roleToActor = new ConcurrentHashMap();

    private Map<WorkerRef, String> actorToRole = new ConcurrentHashMap();

//    private Map<String, WorkerRef> pathToWorkerRef = new ConcurrentHashMap();

    public void register(ActorRef newActorRef, String workerRole) {
        if (!roleToActor.containsKey(workerRole)) {
            List<WorkerRef> actorList = Collections.synchronizedList(new ArrayList<WorkerRef>());
            roleToActor.putIfAbsent(workerRole, actorList);
        }

        WorkerRef newWorkerRef = new WorkerRef(newActorRef);
        roleToActor.get(workerRole).add(newWorkerRef);
        actorToRole.put(newWorkerRef, workerRole);
//        pathToWorkerRef.put(newWorkerRef.path(), newWorkerRef);
    }

    public void unregister(ActorRef newActorRef) {
        String workerRole = actorToRole.get(newActorRef.path());
//        WorkerRef workerRef = pathToWorkerRef.get(newActorRef.path());

        roleToActor.get(workerRole).remove(newActorRef);
        actorToRole.remove(newActorRef);
//        pathToWorkerRef.remove(newActorRef.path());
    }

    /**
     * Get all available {@link WorkerRef} list, by the given worker role.
     *
     * @param workerRole the given role
     * @return available {@link WorkerRef} list
     * @throws NoAvailableWorkerException , when no available worker.
     */
    public List<WorkerRef> availableWorks(String workerRole) throws NoAvailableWorkerException {
        List<WorkerRef> refs = roleToActor.get(workerRole);
        if (refs == null || refs.size() == 0) {
            throw new NoAvailableWorkerException("role=" + workerRole + ", no available worker.");
        }
        return Collections.unmodifiableList(refs);
    }
}

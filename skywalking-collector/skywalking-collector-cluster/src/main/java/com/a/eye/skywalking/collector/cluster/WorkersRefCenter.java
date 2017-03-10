package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.Address;
import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.*;
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

    public void unregister(Address address) {
        Iterator<ActorRef> actorRefToWorkerRefIterator = actorRefToWorkerRef.keySet().iterator();
        while (actorRefToWorkerRefIterator.hasNext()) {
            if (address.equals(actorRefToWorkerRefIterator.next().path().address())) {
                actorRefToWorkerRefIterator.remove();
            }
        }

        Iterator<Map.Entry<String, List<WorkerRef>>> roleToWorkerRefIterator = roleToWorkerRef.entrySet().iterator();
        while (roleToWorkerRefIterator.hasNext()) {
            List<WorkerRef> workerRefList = roleToWorkerRefIterator.next().getValue();

            Iterator<WorkerRef> workerRefIterator = workerRefList.iterator();
            while (workerRefIterator.hasNext()) {
                if (workerRefIterator.next().path().address().equals(address)) {
                    workerRefIterator.remove();
                }
            }
        }
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

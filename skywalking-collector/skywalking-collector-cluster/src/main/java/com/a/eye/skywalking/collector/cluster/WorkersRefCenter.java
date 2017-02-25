package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;

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

    private Map<String, List<ActorRef>> roleToActor = new ConcurrentHashMap();

    private Map<ActorRef, String> actorToRole = new ConcurrentHashMap();

    public void register(ActorRef newRef, String workerRole) {
        if (!roleToActor.containsKey(workerRole)) {
            List<ActorRef> actorList = Collections.synchronizedList(new ArrayList<ActorRef>());
            roleToActor.putIfAbsent(workerRole, actorList);
        }
        roleToActor.get(workerRole).add(newRef);
        actorToRole.put(newRef, workerRole);
    }

    public void unregister(ActorRef newRef) {
        String workerRole = actorToRole.get(newRef);
        roleToActor.get(workerRole).remove(newRef);
        actorToRole.remove(newRef);
    }

    /**
     * Get a copy all available {@link ActorRef} list, by the given worker role.
     * @param workerRole the given role
     * @return available {@link ActorRef} list
     * @throws NoAvailableWorkerException , when no available worker.
     */
    public List<ActorRef> avaibleWorks(String workerRole) throws NoAvailableWorkerException {
        List<ActorRef> refs = roleToActor.get(workerRole);
        if(refs == null || refs.size() == 0){
            throw new NoAvailableWorkerException("role=" + workerRole + ", no available worker.");
        }
        List<ActorRef> availableList = new ArrayList<>(refs.size());
        availableList.addAll(refs);
        return Collections.unmodifiableList(availableList);
    }
}

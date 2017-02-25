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

    public ActorRef find(String workerRole, int sequence) {
        return roleToActor.get(workerRole).get(sequence);
    }

    public int sizeOf(String workerRole) {
        return roleToActor.get(workerRole).size();
    }
}
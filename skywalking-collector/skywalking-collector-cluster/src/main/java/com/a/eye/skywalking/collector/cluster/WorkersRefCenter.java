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

    public void register(ActorRef newRef, String name) {
        if (!roleToActor.containsKey(name)) {
            List<ActorRef> actorList = Collections.synchronizedList(new ArrayList<ActorRef>());
            roleToActor.putIfAbsent(name, actorList);
        }
        roleToActor.get(name).add(newRef);
        actorToRole.put(newRef, name);
    }

    public void unregister(ActorRef newRef) {
        String role = actorToRole.get(newRef);
        roleToActor.get(role).remove(newRef);
        actorToRole.remove(newRef);
    }

//    public ActorRef find(String name, RefRouter router) {
//        return router.find(roleToActor.get(name));
//    }

    public int sizeOf(String name) {
        return roleToActor.get(name).size();
    }
}
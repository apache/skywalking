package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.ActorRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Administrator on 2017/2/21 0021.
 */
public class ActorCache {

    public static Map<String, List<ActorRef>> roleToActor = new ConcurrentHashMap();

    public static Map<ActorRef, String> actorToRole = new ConcurrentHashMap();
}

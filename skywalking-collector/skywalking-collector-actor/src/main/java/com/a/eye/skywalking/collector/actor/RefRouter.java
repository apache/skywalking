package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

import java.util.List;

/**
 * @author wusheng
 */
public interface RefRouter {
    ActorRef find(List<ActorRef> candidates);
}

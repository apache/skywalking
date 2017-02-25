package com.a.eye.skywalking.collector.actor.router;

import akka.actor.ActorRef;

import java.util.List;

/**
 * @author wusheng
 */
public interface WorkerRouter {
    ActorRef find(String workerRole);
}

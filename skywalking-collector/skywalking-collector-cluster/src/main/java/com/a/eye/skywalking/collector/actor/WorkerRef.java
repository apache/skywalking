package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

/**
 * The Worker reference
 *
 * @author pengys5
 */
public class WorkerRef {
    final ActorRef actorRef;

    final String workerRole;

    public WorkerRef(ActorRef actorRef, String workerRole) {
        this.actorRef = actorRef;
        this.workerRole = workerRole;
    }

    void tell(Object message, ActorRef actorRef) {
        actorRef.tell(message, actorRef);
    }

    public String getWorkerRole() {
        return workerRole;
    }
}

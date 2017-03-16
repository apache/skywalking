package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

/**
 * @author pengys5
 */
public class ClusterWorkerRef extends WorkerRef {

    private ActorRef actorRef;

    public ClusterWorkerRef(ActorRef actorRef, Role role) {
        super(role);
        this.actorRef = actorRef;
    }

    @Override
    public void tell(Object message) {
        actorRef.tell(message, ActorRef.noSender());
    }
}

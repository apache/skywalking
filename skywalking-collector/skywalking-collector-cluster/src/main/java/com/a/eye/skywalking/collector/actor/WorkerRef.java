package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

/**
 * @author pengys5
 */
public class WorkerRef {
    final ActorRef actorRef;

    public WorkerRef(ActorRef actorRef) {
        this.actorRef = actorRef;
    }

    void tell(Object message, ActorRef actorRef) {
        actorRef.tell(message, actorRef);
    }

    public String path(){
       return actorRef.path().toString();
    }

    @Override
    public boolean equals(Object obj) {
        return actorRef.equals(obj);
    }

    @Override
    public String toString() {
        return actorRef.toString();
    }
}

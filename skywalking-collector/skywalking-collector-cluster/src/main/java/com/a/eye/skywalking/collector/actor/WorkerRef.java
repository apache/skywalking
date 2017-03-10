package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Worker reference
 *
 * @author pengys5
 */
public class WorkerRef {
    private Logger logger = LogManager.getFormatterLogger(WorkerRef.class);

    final ActorRef actorRef;

    final String workerRole;

    public WorkerRef(ActorRef actorRef, String workerRole) {
        this.actorRef = actorRef;
        this.workerRole = workerRole;
    }

    void tell(Object message, ActorRef sender) {
        logger.debug("tell %s worker", actorRef.toString());
        actorRef.tell(message, sender);
    }

    public ActorPath path() {
        return actorRef.path();
    }

    public String getWorkerRole() {
        return workerRole;
    }
}

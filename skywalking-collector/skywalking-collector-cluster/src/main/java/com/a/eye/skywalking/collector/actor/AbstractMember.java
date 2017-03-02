package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.cluster.WorkersRefCenter;

import java.util.List;

/**
 * @author pengys5
 */
public abstract class AbstractMember<T> {

    private ActorRef actorRef;

    public ActorRef getSelf() {
        return actorRef;
    }

    public void creatorRef(ActorRef actorRef) {
        this.actorRef = actorRef;
    }

    /**
     * Receive the message to analyse.
     *
     * @param message is the data send from the forward worker
     * @throws Throwable is the exception thrown by that worker implementation processing
     */
    public abstract void receive(Object message) throws Throwable;

    /**
     * Send analysed data to next Worker.
     *
     * @param targetWorkerProvider is the worker provider to create worker instance.
     * @param selector             is the selector to select a same role worker instance form cluster.
     * @param message              is the data used to send to next worker.
     * @throws Throwable
     */
    public void tell(AbstractWorkerProvider targetWorkerProvider, WorkerSelector selector, T message) throws Throwable {
        List<WorkerRef> availableWorks = WorkersRefCenter.INSTANCE.availableWorks(targetWorkerProvider.roleName());
        selector.select(availableWorks, message).tell(message, getSelf());
    }
}

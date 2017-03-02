package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public abstract class AbstractLocalWorker<T> implements Worker {

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
     * @param message              is the data used to send to next worker.
     * @throws Throwable
     */
    public void tell(AbstractLocalWorkerProvider targetWorkerProvider, T message) throws Throwable {
        LocalSystem.actorFor(targetWorkerProvider.getClass(), targetWorkerProvider.roleName());
    }
}

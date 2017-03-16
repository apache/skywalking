package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider<T extends AbstractWorker> implements Provider {

    public abstract Role role();

    public abstract Class<T> workerClass();

//    public abstract WorkerSelector selector();

    final void validate() throws Exception {
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from workerClass()");
        }
    }
}

package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;

/**
 * @author pengys5
 */
public abstract class AbstractLocalWorkerProvider extends AbstractWorkerProvider<LocalSystem> {

    /**
     * Use {@link ActorSystem} to Create worker instance with the {@link #workerClass()} method returned class.
     *
     * @param system is a akka {@link ActorSystem} instance.
     */
    @Override
    public void createWorker(LocalSystem system) {
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from workerClass()");
        }
        if (workerNum() <= 0) {
            throw new IllegalArgumentException("cannot createInstance() with obtained from workerNum() must greater than 0");
        }

        for (int i = 1; i <= workerNum(); i++) {
            LocalSystem.actorOf(getClass(), roleName());
        }
    }
}

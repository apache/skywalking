package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * @author pengys5
 */
public abstract class AbstractClusterWorkerProvider extends AbstractWorkerProvider<ActorSystem> {

    @Override
    public void createWorker(ActorSystem system) {
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from workerClass()");
        }
        if (workerNum() <= 0) {
            throw new IllegalArgumentException("cannot createInstance() with obtained from workerNum() must greater than 0");
        }

        for (int i = 1; i <= workerNum(); i++) {
            system.actorOf(Props.create(workerClass()), roleName() + "_" + i);
        }
    }

}

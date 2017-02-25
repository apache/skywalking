package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.api.util.StringUtil;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider {

    public abstract String workerRole();

    public abstract Class workerClass();

    public abstract int workerNum();

    public void createWorker(ActorSystem system) {
        if (StringUtil.isEmpty(workerRole())) {
            throw new IllegalArgumentException("cannot createWorker() with nothing obtained from workerRole()");
        }
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createWorker() with nothing obtained from workerClass()");
        }
        if (workerNum() <= 0) {
            throw new IllegalArgumentException("cannot createWorker() with obtained from workerNum() must greater than 0");
        }

        for (int i = 1; i <= workerNum(); i++) {
            system.actorOf(Props.create(workerClass(), workerRole()), workerRole() + "_" + i);
        }
    }
}

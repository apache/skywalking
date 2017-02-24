package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.api.util.StringUtil;

/**
 * @author pengys5
 */
public abstract class AbstractWorkerProvider {

    public abstract String workerName();

    public abstract Class workerClass();

    public abstract int workerNum();

    public void createWorker(ActorSystem system) {
        if (StringUtil.isEmpty(workerName())) {
            throw new IllegalArgumentException("cannot createWorker() with anything not obtained from workerName()");
        }
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createWorker() with anything not obtained from workerClass()");
        }
        if (workerNum() <= 0) {
            throw new IllegalArgumentException("cannot workerNum() with obtained from workerNum() must greater than 0");
        }

        for (int i = 1; i <= workerNum(); i++) {
            system.actorOf(Props.create(workerClass()), workerName() + "_" + i);
        }
    }
}

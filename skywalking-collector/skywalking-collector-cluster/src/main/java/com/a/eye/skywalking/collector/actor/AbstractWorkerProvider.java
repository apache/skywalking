package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * The <code>AbstractWorkerProvider</code> should be implemented by any class whose
 * instances are intended to provide create instance of the {@link AbstractWorker}.
 * <p>
 * Here is an example on how to create and use an {@link AbstractWorkerProvider}:
 * <p>
 * {{{
 * public class SampleWorkerFactory extends AbstractWorkerProvider {
 *
 *      @Override public Class workerClass() {
 *          return SampleWorker.class;
 *      }
 *
 *      @Override public int workerNum() {
 *          return Config.SampleWorkerNum;
 *      }
 * }
 * }}}
 *
 * @author pengys5
 */
public abstract class AbstractWorkerProvider {

    public abstract Class workerClass();

    public abstract int workerNum();

    /**
     * Use {@link ActorSystem} to Create worker instance with the {@link #workerClass()} method returned class.
     *
     * @param system is a akka {@link ActorSystem} instance.
     */
    public void createWorker(ActorSystem system) {
        if (workerClass() == null) {
            throw new IllegalArgumentException("cannot createWorker() with nothing obtained from workerClass()");
        }
        if (workerNum() <= 0) {
            throw new IllegalArgumentException("cannot createWorker() with obtained from workerNum() must greater than 0");
        }

        for (int i = 1; i <= workerNum(); i++) {
            system.actorOf(Props.create(workerClass()), roleName() + "_" + i);
        }
    }

    /**
     * Use {@link #workerClass()} method returned class's simple name as a role name.
     *
     * @return is role of Worker
     */
    protected String roleName() {
        return workerClass().getSimpleName();
    }
}

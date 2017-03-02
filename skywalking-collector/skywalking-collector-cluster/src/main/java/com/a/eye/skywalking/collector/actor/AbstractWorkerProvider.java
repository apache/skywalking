package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * The <code>AbstractWorkerProvider</code> should be implemented by any class whose
 * instances are intended to provide create instance of the {@link AbstractWorker}.
 * The {@link WorkersCreator} use java service loader to load provider implementer,
 * so you should config the service file.
 * <p>
 * Here is an example on how to create and use an {@link AbstractWorkerProvider}:
 * <p>
 * {{{
 * public class SampleWorkerFactory extends AbstractWorkerProvider {
 *
 * @author pengys5
 * @Override public Class workerClass() {
 * return SampleWorker.class;
 * }
 * @Override public int workerNum() {
 * return Config.SampleWorkerNum;
 * }
 * }
 * }}}
 * <p>
 */
public abstract class AbstractWorkerProvider<T> {

    public abstract Class workerClass();

    public abstract int workerNum();

    public abstract void createWorker(T system);

    /**
     * Use {@link #workerClass()} method returned class's simple name as a role name.
     *
     * @return is role of Worker
     */
    protected String roleName() {
        return workerClass().getSimpleName();
    }
}

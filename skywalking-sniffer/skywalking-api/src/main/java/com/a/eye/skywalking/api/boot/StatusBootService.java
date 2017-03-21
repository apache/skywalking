package com.a.eye.skywalking.api.boot;

/**
 * The <code>StatusBootService</code> is an abstract implementations of {@link BootService},
 * it extends {@link BootService}'s ability like this:
 *
 * If an implementation extends <code>StatusBootService</code>, it can know whether this service starts up successfully or not.
 * It's based on {@link #bootUp()} function finished with or without an exception. if no exception, means start up successfully.
 *
 *
 * @author wusheng
 */
public abstract class StatusBootService implements BootService {
    private volatile boolean started = false;

    protected boolean isStarted(){
        return this.started;
    }

    @Override
    public final void bootUp() throws Throwable{
        try {
            bootUpWithStatus();
            started = true;
        } catch (Throwable e) {
            started = false;
            throw e;
        }
    }

    protected abstract void bootUpWithStatus() throws Exception;
}

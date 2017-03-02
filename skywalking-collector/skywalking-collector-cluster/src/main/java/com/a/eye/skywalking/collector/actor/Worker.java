package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public interface Worker {

    public void receive(Object message) throws Throwable;
}

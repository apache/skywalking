package com.a.eye.skywalking.collector.actor.selector;

/**
 * @author pengys5
 */
public abstract class AbstractHashMessage {
    private int hashCode;

    public AbstractHashMessage(String key) {
        this.hashCode = key.hashCode();
    }

    protected int getHashCode() {
        return hashCode;
    }
}

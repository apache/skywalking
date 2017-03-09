package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public abstract class AbstractHashMessage {
    private int hashCode;

    public void setHashCode(String key) {
        this.hashCode = key.hashCode();
    }

    public int getHashCode() {
        return hashCode;
    }
}

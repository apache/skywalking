package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public class Promise {
    private boolean isTold = false;
    private Object value;

    protected void completed(Object value) {
        this.value = value;
        isTold = true;
    }

    public boolean isTold() {
        return isTold;
    }
}

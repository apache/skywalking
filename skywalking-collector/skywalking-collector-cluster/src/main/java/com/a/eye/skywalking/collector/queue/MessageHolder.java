package com.a.eye.skywalking.collector.queue;

/**
 * @author pengys5
 */
public class MessageHolder<T> {
    private T message;

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }

    public void reset() {
        message = null;
    }
}

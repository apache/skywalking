package org.skywalking.apm.collector.queue;

/**
 * @author pengys5
 */
public class MessageHolder {
    private Object message;

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public void reset() {
        message = null;
    }
}

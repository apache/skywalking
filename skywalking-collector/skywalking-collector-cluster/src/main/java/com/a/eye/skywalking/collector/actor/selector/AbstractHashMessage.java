package com.a.eye.skywalking.collector.actor.selector;

/**
 * The <code>AbstractHashMessage</code> should be implemented by any class whose instances
 * are intended to provide send message with {@link HashCodeSelector}.
 * <p>
 * Usually the implemented class used to persistence data to database
 * or aggregation the metric,
 *
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

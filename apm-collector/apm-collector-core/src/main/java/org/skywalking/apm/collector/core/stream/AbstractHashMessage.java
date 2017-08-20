package org.skywalking.apm.collector.core.stream;

/**
 * The <code>AbstractHashMessage</code> implementations represent aggregate message,
 * which use to aggregate metric.
 * <p>
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractHashMessage {
    private int hashCode;

    public AbstractHashMessage(String key) {
        this.hashCode = key.hashCode();
    }

    public int getHashCode() {
        return hashCode;
    }
}

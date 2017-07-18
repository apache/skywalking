package org.skywalking.apm.collector.stream.selector;

/**
 * The <code>AbstractHashMessage</code> implementations represent aggregate message,
 * which use to aggregate metric.
 * Make the message aggregator's worker selector use of {@link HashCodeSelector}.
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

    int getHashCode() {
        return hashCode;
    }
}

package org.skywalking.apm.collector.agentstream.worker.util;

/**
 * @author pengys5
 */
public enum ExchangeMarkUtils {
    INSTANCE;

    private static final String MARK_TAG = "M";

    public String buildMarkedID(int id) {
        return MARK_TAG + id;
    }
}

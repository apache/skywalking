package org.skywalking.apm.collector.core.framework;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public interface Starter {
    void start() throws CollectorException;
}

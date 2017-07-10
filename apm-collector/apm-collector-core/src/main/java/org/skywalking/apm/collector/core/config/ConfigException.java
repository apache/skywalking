package org.skywalking.apm.collector.core.config;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class ConfigException extends CollectorException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

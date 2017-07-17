package org.skywalking.apm.collector.core.config;

/**
 * @author pengys5
 */
public abstract class ConfigLoaderException extends ConfigException {

    public ConfigLoaderException(String message) {
        super(message);
    }

    public ConfigLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}

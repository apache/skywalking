package org.skywalking.apm.collector.core.config;

/**
 * @author pengys5
 */
public class ConfigParseException extends ConfigException {

    public ConfigParseException(String message) {
        super(message);
    }

    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

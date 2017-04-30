package org.skywalking.apm.agent.core.plugin;

public class PluginException extends RuntimeException {
    private static final long serialVersionUID = -6020188711867490724L;

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}

package org.skywalking.apm.agent.core.plugin.interceptor;

import org.skywalking.apm.agent.core.plugin.PluginException;

public class EnhanceException extends PluginException {
    private static final long serialVersionUID = -2234782755784217255L;

    public EnhanceException(String message) {
        super(message);
    }

    public EnhanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

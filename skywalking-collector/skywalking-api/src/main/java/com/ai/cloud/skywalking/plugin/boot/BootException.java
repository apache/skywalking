package com.ai.cloud.skywalking.plugin.boot;

import com.ai.cloud.skywalking.plugin.PluginException;

public class BootException extends PluginException {
	private static final long serialVersionUID = 8618884011525098003L;
	
	public BootException(String message) {
        super(message);
    }

    public BootException(String message, Throwable cause) {
        super(message, cause);
    }

}

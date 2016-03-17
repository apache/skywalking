package com.ai.cloud.skywalking.plugin.interceptor;

public class EnhanceException extends Exception {
	private static final long serialVersionUID = -2234782755784217255L;

	public EnhanceException(String message) {
        super(message);
    }

    public EnhanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

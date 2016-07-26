package com.ai.cloud.skywalking.plugin.exception;

public class EnhanceClassEmptyException extends PluginException{

    public EnhanceClassEmptyException(String message) {
        super(message);
    }

    public EnhanceClassEmptyException(String message, Throwable cause) {
        super(message, cause);
    }
}

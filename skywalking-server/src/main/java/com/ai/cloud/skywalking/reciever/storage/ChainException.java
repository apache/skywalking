package com.ai.cloud.skywalking.reciever.storage;

public class ChainException extends RuntimeException {
	private static final long serialVersionUID = -3134195788063272909L;

	public ChainException(Throwable cause) {
        super(cause);
    }

    public ChainException(String message, Throwable cause) {
        super(message, cause);
    }
}

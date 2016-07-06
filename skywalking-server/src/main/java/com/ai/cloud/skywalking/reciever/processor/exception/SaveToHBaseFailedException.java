package com.ai.cloud.skywalking.reciever.processor.exception;

/**
 * Created by xin on 16-7-6.
 */
public class SaveToHBaseFailedException extends RuntimeException {
    public SaveToHBaseFailedException(Exception e) {
        super(e);
    }
}

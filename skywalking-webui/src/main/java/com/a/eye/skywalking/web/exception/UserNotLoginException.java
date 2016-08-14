package com.a.eye.skywalking.web.exception;

public class UserNotLoginException extends RuntimeException {

    public UserNotLoginException(String message) {
        super(message);
    }
}

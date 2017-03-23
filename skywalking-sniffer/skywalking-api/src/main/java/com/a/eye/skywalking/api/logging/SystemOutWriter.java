package com.a.eye.skywalking.api.logging;

public enum SystemOutWriter implements IWriter {
    INSTANCE;

    @Override
    public void write(String message) {
        System.out.println(message);
    }
}

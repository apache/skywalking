package com.ai.cloud.skywalking.logging;

public class STDOutWriter implements IWriter {


    @Override
    public void write(String message) {
        System.err.println(message);
    }
}

package com.a.eye.skywalking.logging;

public class STDOutWriter implements IWriter {


    @Override
    public void write(String message) {
        System.out.println(message);
    }

    @Override
    public void writeError(String message) {
        System.err.println(message);
    }
}

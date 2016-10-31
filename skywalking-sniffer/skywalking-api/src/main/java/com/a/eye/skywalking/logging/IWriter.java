package com.a.eye.skywalking.logging;

public interface IWriter {
    void write(String message);

    void writeError(String message);
}

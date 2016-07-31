package com.ai.cloud.skywalking.logging;

public interface IWriter {
    void write(String message);

    void writeError(String message);
}

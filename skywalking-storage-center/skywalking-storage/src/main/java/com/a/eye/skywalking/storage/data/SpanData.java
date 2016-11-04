package com.a.eye.skywalking.storage.data;

public interface SpanData {

    long getStartTime();

    byte[] toByteArray();
}

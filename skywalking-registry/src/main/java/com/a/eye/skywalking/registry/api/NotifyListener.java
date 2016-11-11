package com.a.eye.skywalking.registry.api;

public interface NotifyListener {
    void notify(EventType type, String urls);
}

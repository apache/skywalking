package com.a.eye.skywalking.registry.logging.api;

public interface NotifyListener {
    void notify(EventType type, String urls);
}

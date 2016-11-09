package com.a.eye.skywalking.registry.api;

import java.util.List;

public interface NotifyListener {
    void notify(List<String> urls);
}

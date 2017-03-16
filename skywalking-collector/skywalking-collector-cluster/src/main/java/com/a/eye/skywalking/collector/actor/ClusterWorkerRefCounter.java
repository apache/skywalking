package com.a.eye.skywalking.collector.actor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pengys5
 */
public enum ClusterWorkerRefCounter {
    INSTANCE;

    private Map<String, AtomicInteger> counter = new ConcurrentHashMap<>();

    public int incrementAndGet(Role role) {
        if (!counter.containsKey(role.name())) {
            AtomicInteger atomic = new AtomicInteger(0);
            counter.putIfAbsent(role.name(), atomic);
        }
        return counter.get(role.name()).incrementAndGet();
    }
}

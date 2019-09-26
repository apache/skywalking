package org.apache.skywalking.plugin.test.mockcollector.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Sequences {

    public static final AtomicInteger INSTANCE_SEQUENCE = new AtomicInteger();

    public static final AtomicInteger ENDPOINT_SEQUENCE = new AtomicInteger(1);

    public static final ConcurrentHashMap<String, Integer> SERVICE_MAPPING = new ConcurrentHashMap<String, Integer>();
}

package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.Gson;

public enum InstanceDeserialize {
    INSTANCE;

    public Registry deserializeRegistryInfo(String registryInfoStr) {
        return new Gson().fromJson(registryInfoStr, Registry.class);
    }

    public Ping deserializePingInfo(String pingInfoStr) {
        return new Gson().fromJson(pingInfoStr, Ping.class);
    }
}

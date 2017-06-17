package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.Gson;

public enum InstanceDeserialize {
    INSTANCE;

    public RegistryInfo deserializeRegistryInfo(String registryInfoStr) {
        return new Gson().fromJson(registryInfoStr, RegistryInfo.class);
    }

    public PingInfo deserializePingInfo(String pingInfoStr) {
        return new Gson().fromJson(pingInfoStr, PingInfo.class);
    }
}

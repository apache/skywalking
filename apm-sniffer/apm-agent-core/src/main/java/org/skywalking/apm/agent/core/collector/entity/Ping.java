package org.skywalking.apm.agent.core.collector.entity;

import com.google.gson.annotations.SerializedName;

public class Ping {
    @SerializedName(value = "ii")
    private long instanceId;

    public Ping(int instanceId) {
        this.instanceId = instanceId;
    }
}

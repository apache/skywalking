package org.skywalking.apm.agent.core.collector.entity;

import com.google.gson.annotations.SerializedName;

public class PingInfo {
    @SerializedName(value = "ii")
    private long instanceId;

    public PingInfo(int instanceId) {
        this.instanceId = instanceId;
    }
}

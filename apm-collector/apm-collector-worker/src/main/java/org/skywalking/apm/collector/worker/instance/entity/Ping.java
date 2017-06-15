package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.annotations.SerializedName;

public class Ping {

    @SerializedName("ii")
    private long instanceId = -1;

    public long getInstanceId() {
        return instanceId;
    }
}

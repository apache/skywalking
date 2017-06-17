package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.annotations.SerializedName;

public class PingInfo {

    @SerializedName("ii")
    private int instanceId = -1;

    public int getInstanceId() {
        return instanceId;
    }
}

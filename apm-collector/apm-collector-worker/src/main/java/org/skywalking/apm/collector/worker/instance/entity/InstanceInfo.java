package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class InstanceInfo {
    @Expose
    @SerializedName(value = "ac")
    private String applicationCode;
    @Expose
    @SerializedName(value = "ii")
    private long instanceId;
    @Expose
    @SerializedName(value = "rt")
    private long registryTime;
    @Expose
    @SerializedName(value = "pt")
    private long lastPingTime;

    public InstanceInfo(String applicationCode, long instanceId) {
        this.applicationCode = applicationCode;
        this.instanceId = instanceId;
        registryTime = System.currentTimeMillis();
        lastPingTime = registryTime;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public String serialize() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this);
    }
}

package org.skywalking.apm.agent.core.collector.entity;

import com.google.gson.annotations.SerializedName;

public class HeartBeatInfo {
    @SerializedName(value = "ac")
    private String applicationCode;

    public HeartBeatInfo(String applicationCode) {
        this.applicationCode = applicationCode;
    }
}

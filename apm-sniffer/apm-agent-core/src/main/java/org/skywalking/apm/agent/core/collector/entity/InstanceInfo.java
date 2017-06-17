package org.skywalking.apm.agent.core.collector.entity;

import com.google.gson.annotations.SerializedName;

public class InstanceInfo {

    @SerializedName(value = "ac")
    private String applicationCode;

    public InstanceInfo(String applicationCode) {
        this.applicationCode = applicationCode;
    }
}

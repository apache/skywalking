package org.skywalking.apm.agent.core.collector.entity;

import com.google.gson.annotations.SerializedName;

public class Instance {

    @SerializedName(value = "ac")
    private String applicationCode;

    public Instance(String applicationCode) {
        this.applicationCode = applicationCode;
    }
}

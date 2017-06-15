package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.annotations.SerializedName;

public class Registry {

    @SerializedName("ac")
    private String applicationCode;

    public String getApplicationCode() {
        return applicationCode;
    }
}

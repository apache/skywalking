package org.skywalking.apm.collector.worker.instance.entity;

import com.google.gson.annotations.SerializedName;

public class RegistryInfo {

    @SerializedName("ac")
    private String applicationCode;

    public String getApplicationCode() {
        return applicationCode;
    }
}

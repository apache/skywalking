package org.skywalking.apm.collector.worker.instance.entity;

public class RegistryInfo {
    private String applicationCode;

    public RegistryInfo(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getApplicationCode() {
        return applicationCode;
    }
}

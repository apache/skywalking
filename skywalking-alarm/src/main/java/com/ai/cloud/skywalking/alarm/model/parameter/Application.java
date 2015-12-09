package com.ai.cloud.skywalking.alarm.model.parameter;

import java.util.Collection;
import java.util.Set;

public class Application {

    private String applicationId;

    private Set<String> traceIds;

    public Application(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Set<String> getTraceIds() {
        return traceIds;
    }

    public void setTraceIds(Set<String> traceIds) {
        this.traceIds = traceIds;
    }


}

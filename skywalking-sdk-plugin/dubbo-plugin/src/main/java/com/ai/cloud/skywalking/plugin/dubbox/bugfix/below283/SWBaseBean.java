package com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283;

import java.io.Serializable;

public class SWBaseBean implements Serializable {
    private String contextData;

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }
}

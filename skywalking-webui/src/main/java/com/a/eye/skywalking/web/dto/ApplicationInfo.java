package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.web.entity.Application;

public class ApplicationInfo extends Application {

    public ApplicationInfo(String applicationId) {
        super(applicationId);
    }

    private boolean hasGlobalAlarmRule;
    private boolean isGlobalAlarmRule;
    private boolean isUpdateGlobalConfig;
    private boolean isGlobalConfig;

    public boolean isGlobalConfig() {
        return isGlobalConfig;
    }

    public boolean isUpdateGlobalConfig() {
        return isUpdateGlobalConfig;
    }

    public void setHasGlobalAlarmRule(boolean hasGlobalAlarmRule) {
        this.hasGlobalAlarmRule = hasGlobalAlarmRule;
    }

    public void setGlobalAlarmRule(boolean globalAlarmRule) {
        isGlobalAlarmRule = globalAlarmRule;
    }
}

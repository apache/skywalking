package com.ai.cloud.skywalking.web.bo;

import com.ai.cloud.skywalking.web.entity.Application;

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

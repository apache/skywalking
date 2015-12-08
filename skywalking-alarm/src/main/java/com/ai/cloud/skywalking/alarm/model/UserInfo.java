package com.ai.cloud.skywalking.alarm.model;

import java.util.List;

public class UserInfo {
    private String userId;

    private List<ApplicationInfo> applicationInfos;

    public UserInfo(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public List<ApplicationInfo> getApplicationInfos() {
        return applicationInfos;
    }

    public void setApplicationInfos(List<ApplicationInfo> applicationInfos) {
        this.applicationInfos = applicationInfos;
    }
}

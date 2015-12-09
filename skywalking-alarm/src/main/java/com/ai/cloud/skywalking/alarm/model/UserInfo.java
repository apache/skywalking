package com.ai.cloud.skywalking.alarm.model;

import java.util.List;

public class UserInfo {

    private String userId;

    private List<AlarmRule> rules;

    public UserInfo(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public List<AlarmRule> getRules() {
        return rules;
    }

    public void setRules(List<AlarmRule> rules) {
        this.rules = rules;
    }
}

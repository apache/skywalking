package com.ai.cloud.skywalking.alarm.model;

import java.util.List;

public class ProcessThreadValue {
    private String status;
    private List<String> dealUserIds;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getDealUserIds() {
        return dealUserIds;
    }

    public void setDealUserIds(List<String> dealUserIds) {
        this.dealUserIds = dealUserIds;
    }
}

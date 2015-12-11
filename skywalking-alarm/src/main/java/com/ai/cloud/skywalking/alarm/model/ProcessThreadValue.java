package com.ai.cloud.skywalking.alarm.model;

import java.util.List;

public class ProcessThreadValue {
    private int status;
    private List<String> dealUserIds;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<String> getDealUserIds() {
        return dealUserIds;
    }

    public void setDealUserIds(List<String> dealUserIds) {
        this.dealUserIds = dealUserIds;
    }
}

package com.ai.cloud.skywalking.analysis.categorize2chain.model;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class ChainNode {
    @Expose
    private String nodeToken;
    @Expose
    private String viewPoint;
    @Expose
    private String businessKey;

    private long cost;
    private NodeStatus status;
    @Expose
    private String parentLevelId;
    @Expose
    private int levelId;
    @Expose
    private String callType;
    private long startDate;
    @Expose
    private String userId;

    public String getNodeToken() {
        return nodeToken;
    }

    public void setNodeToken(String nodeToken) {
        this.nodeToken = nodeToken;
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public void setViewPoint(String viewPoint) {
        this.viewPoint = viewPoint;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getParentLevelId() {
        return parentLevelId;
    }

    public void setParentLevelId(String parentLevelId) {
        this.parentLevelId = parentLevelId;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getTraceLevelId() {
        StringBuilder stringBuilder = new StringBuilder();
        if (getParentLevelId() != null && getParentLevelId().length() > 0) {
            stringBuilder.append(getParentLevelId() + ".");
        }
        return stringBuilder.append(getLevelId()).toString();
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getStartDate() {
        return startDate;
    }

    public enum NodeStatus {
        NORMAL('N'), ABNORMAL('A'), HUMAN_INTERRUPTION('I');
        private char value;

        NodeStatus(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }

        public static NodeStatus convert(char value) {
            switch (value) {
                case 'N':
                    return NORMAL;
                case 'A':
                    return ABNORMAL;
                case 'I':
                	return HUMAN_INTERRUPTION;
                default:
                    throw new IllegalStateException("Failed to convert[" + value + "]");
            }
        }
    }

    @Override
    public String toString() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this);
    }
}

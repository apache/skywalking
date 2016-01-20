package com.ai.cloud.skywalking.analysis.model;

public class ChainNode {

    private String nodeToken;

    private String viewPoint;
    private String businessKey;
    private long cost;
    private NodeStatus status;
    private String parentLevelId;
    private int levelId;
    private String callType;
    private long startDate;

    // 不参与序列化
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
        NORMAL('N'), ABNORMAL('A');
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
                default:
                    throw new IllegalStateException("Failed to convert[" + value + "]");
            }
        }
    }
}

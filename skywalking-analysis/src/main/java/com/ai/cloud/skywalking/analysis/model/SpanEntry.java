package com.ai.cloud.skywalking.analysis.model;

import com.ai.cloud.skywalking.protocol.CallType;
import com.ai.cloud.skywalking.protocol.Span;

public class SpanEntry {

    private Span clientSpan;
    private Span serverSpan;

    public SpanEntry() {

    }

    public int getLevelId() {
        if (clientSpan != null) {
            return clientSpan.getLevelId();
        }

        return serverSpan.getLevelId();
    }

    public String getParentLevelId() {
        if (clientSpan != null) {
            return clientSpan.getParentLevel();
        }

        return serverSpan.getParentLevel();
    }

    public String getViewPoint() {
        if (clientSpan != null) {
            return clientSpan.getViewPointId();
        }

        return serverSpan.getViewPointId();
    }

    public CallType getCallType() {
        if (clientSpan != null) {
            return CallType.convert(clientSpan.getCallType());
        }

        return CallType.convert(serverSpan.getCallType());
    }

    public long getCost() {
        long resultCost = 0;
        switch (getCallType()) {
            case ASYNC:
                resultCost = getClientCost() + getServerCost();
                break;
            case SYNC:
                resultCost = getClientCost();
                if (getClientSpan() == null) {
                    resultCost = getServerCost();
                }
                break;
            case LOCAL:
                resultCost = getClientCost();
                break;
        }

        return resultCost;
    }

    public long getClientCost() {
        if (clientSpan != null) {
            return clientSpan.getCost();
        }

        return 0;
    }

    public long getServerCost() {
        if (serverSpan != null) {
            return serverSpan.getCost();
        }

        return 0;
    }

    public String getBusinessKey() {
        if (clientSpan != null) {
            return clientSpan.getBusinessKey();
        }

        return serverSpan.getBusinessKey();
    }

    public ChainNode.NodeStatus getSpanStatus() {
        if (clientSpan != null) {
            if (clientSpan.getExceptionStack() != null && clientSpan.getExceptionStack().length() > 0) {
                return ChainNode.NodeStatus.ABNORMAL;
            }
        }

        if (serverSpan != null) {
            if (serverSpan.getExceptionStack() != null && serverSpan.getExceptionStack().length() > 0) {
                return ChainNode.NodeStatus.ABNORMAL;
            }
        }

        return ChainNode.NodeStatus.NORMAL;
    }

    public String getExceptionStack() {
        StringBuilder exceptionStack = new StringBuilder();
        if (clientSpan != null) {
            exceptionStack.append("Client" + clientSpan.getExceptionStack());
        }

        if (serverSpan != null) {
            exceptionStack.append("Server cause by :" + serverSpan.getExceptionStack());
        }

        return exceptionStack.toString();
    }

    public String getApplicationId() {
        if (clientSpan != null) {
            return clientSpan.getApplicationId();
        }

        return serverSpan.getApplicationId();
    }

    public Span getClientSpan() {
        return clientSpan;
    }

    public Span getServerSpan() {
        return serverSpan;
    }

    public void setClientSpan(Span clientSpan) {
        this.clientSpan = clientSpan;
    }

    public void setServerSpan(Span serverSpan) {
        this.serverSpan = serverSpan;
    }

    public String getSpanType() {
        if (clientSpan != null) {
            return clientSpan.getSpanType();
        }
        return serverSpan.getSpanType();
    }

    public String getUserId() {
        if (clientSpan != null) {
            return clientSpan.getUserId();
        }
        return serverSpan.getUserId();
    }
}

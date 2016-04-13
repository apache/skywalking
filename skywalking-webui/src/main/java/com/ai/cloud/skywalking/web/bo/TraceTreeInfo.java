package com.ai.cloud.skywalking.web.bo;

import java.util.List;

public class TraceTreeInfo {
    private String traceId;
    private long beginTime;
    private long endTime;
    private List<TraceNodeInfo> nodes;
    private int nodeSize;

    public TraceTreeInfo(String traceId, List<TraceNodeInfo> nodes) {
        this.traceId = traceId;
        this.nodes = nodes;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<TraceNodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<TraceNodeInfo> nodes) {
        this.nodes = nodes;
    }

    public void setNodeSize(int nodeSize) {
        this.nodeSize = nodeSize;
    }

    public int getNodeSize() {
        return nodeSize;
    }
}

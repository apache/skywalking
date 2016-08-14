package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.web.util.Constants;
import com.a.eye.skywalking.web.util.TokenGenerator;

import java.util.List;

public class TraceTreeInfo {
    private String traceId;
    private boolean hasCallChainTree = true;
    private String callChainTreeToken = "";
    private long beginTime;
    private long endTime;
    private List<TraceNodeInfo> nodes;
    private int nodeSize;
    private int maxShowNodeSize = Constants.MAX_SHOW_SPAN_SIZE;
    private int maxQueryNodeSize = Constants.MAX_SEARCH_SPAN_SIZE;

    public TraceTreeInfo(String traceId) {
        this.traceId = traceId;
    }

    public TraceTreeInfo(String traceId, List<TraceNodeInfo> nodes) {
        this.traceId = traceId;
        nodeSize = nodes.size();
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

    public void setHasBeenSpiltNodes(List<TraceNodeInfo> nodes) {
        this.nodes = nodes;
    }

    public void setRealNodeSize(int nodeSize) {
        this.nodeSize = nodeSize;
    }

    public int getNodeSize() {
        return nodeSize;
    }

    public int getMaxShowNodeSize() {
        return maxShowNodeSize;
    }

    public void setMaxShowNodeSize(int maxShowNodeSize) {
        this.maxShowNodeSize = maxShowNodeSize;
    }

    public int getMaxQueryNodeSize() {
        return maxQueryNodeSize;
    }

    public void setMaxQueryNodeSize(int maxQueryNodeSize) {
        this.maxQueryNodeSize = maxQueryNodeSize;
    }

    public void fillCallChainTreeToken(String entranceViewPoint) {
        if (entranceViewPoint == null || entranceViewPoint.length() == 0){
            hasCallChainTree = false;
        }
        callChainTreeToken = TokenGenerator.generateTreeToken(entranceViewPoint);
    }

    public boolean isHasCallChainTree() {
        return hasCallChainTree;
    }

    public void setHasCallChainTree(boolean hasCallChainTree) {
        this.hasCallChainTree = hasCallChainTree;
    }

    public String getCallChainTreeToken() {
        return callChainTreeToken;
    }

    public void setCallChainTreeToken(String callChainTreeToken) {
        this.callChainTreeToken = callChainTreeToken;
    }
}

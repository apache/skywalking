package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.util.ViewPointBeautiUtil;

/**
 * Created by xin on 16-4-14.
 */
public class CallChainNode {
    private String traceLevelId="";
    private String viewPoint;
    private boolean isGuess;
    public CallChainNode(String traceLevelId, String viewPoint, boolean isGuess) {
        this.traceLevelId = traceLevelId;
        this.viewPoint = viewPoint;
        this.isGuess = isGuess;
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void setTraceLevelId(String traceLevelId) {
        this.traceLevelId = traceLevelId;
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public void setViewPoint(String viewPoint) {
        this.viewPoint = viewPoint;
    }

    public void beautiViewPointString(String searchKey) {
        if (viewPoint.length() > 100) {
            viewPoint = ViewPointBeautiUtil.beautifulViewPoint(viewPoint, searchKey);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallChainNode that = (CallChainNode) o;

        return traceLevelId != null ? traceLevelId.equals(that.traceLevelId) : that.traceLevelId == null;

    }

    @Override
    public int hashCode() {
        return traceLevelId != null ? traceLevelId.hashCode() : 0;
    }
}

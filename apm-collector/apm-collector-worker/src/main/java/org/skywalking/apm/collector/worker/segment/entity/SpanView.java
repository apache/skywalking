package org.skywalking.apm.collector.worker.segment.entity;

import java.util.HashSet;
import java.util.Set;

/**
 * @author pengys5
 */
public class SpanView implements Comparable<SpanView> {

    private int spanId;
    private String segId;
    private String appCode;
    private String spanSegId;
    private String parentSpanSegId;
    private long startTime;
    private long relativeStartTime;
    private long cost;
    private String operationName;
    private Set<SpanView> childSpans;

    public SpanView() {
        childSpans = new HashSet<>();
    }

    public int getSpanId() {
        return spanId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public String getSegId() {
        return segId;
    }

    public void setSegId(String segId) {
        this.segId = segId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getSpanSegId() {
        return spanSegId;
    }

    public void setSpanSegId(String spanSegId) {
        this.spanSegId = spanSegId;
    }

    public String getParentSpanSegId() {
        return parentSpanSegId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setParentSpanSegId(String parentSpanSegId) {
        this.parentSpanSegId = parentSpanSegId;
    }

    public long getRelativeStartTime() {
        return relativeStartTime;
    }

    public void setRelativeStartTime(long relativeStartTime) {
        this.relativeStartTime = relativeStartTime;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public void addChild(SpanView childSpan) {
        childSpans.add(childSpan);
    }

    @Override
    public int compareTo(SpanView o) {
        return Long.valueOf(this.startTime - o.getStartTime()).intValue();
    }
}

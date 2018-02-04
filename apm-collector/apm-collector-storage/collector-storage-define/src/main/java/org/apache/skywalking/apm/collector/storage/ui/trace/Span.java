/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.storage.ui.trace;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peng-yongsheng
 */
public class Span {
    private String traceId;
    private String segmentId;
    private int spanId;
    private int parentSpanId;
    private List<Ref> refs;
    private String applicationCode;
    private long startTime;
    private long endTime;
    private String operationName;
    private String type;
    private String peer;
    private String component;
    private Boolean isError;
    private String layer;
    private List<KeyValue> tags;
    private List<LogEntity> logs;
    private boolean isRoot;
    private String segmentSpanId;
    private String segmentParentSpanId;

    public Span() {
        this.refs = new LinkedList<>();
        this.tags = new LinkedList<>();
        this.logs = new LinkedList<>();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public int getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(int parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public List<Ref> getRefs() {
        return refs;
    }

    public void setRefs(List<Ref> refs) {
        this.refs = refs;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Boolean getError() {
        return isError;
    }

    public void setError(Boolean error) {
        isError = error;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public List<KeyValue> getTags() {
        return tags;
    }

    public void setTags(List<KeyValue> tags) {
        this.tags = tags;
    }

    public List<LogEntity> getLogs() {
        return logs;
    }

    public void setLogs(List<LogEntity> logs) {
        this.logs = logs;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setRoot(boolean root) {
        isRoot = root;
    }

    public String getSegmentParentSpanId() {
        return segmentParentSpanId;
    }

    public void setSegmentParentSpanId(String segmentParentSpanId) {
        this.segmentParentSpanId = segmentParentSpanId;
    }

    public String getSegmentSpanId() {
        return segmentSpanId;
    }

    public void setSegmentSpanId(String segmentSpanId) {
        this.segmentSpanId = segmentSpanId;
    }
}

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

import java.util.*;

/**
 * @author peng-yongsheng
 */
public class BasicTrace {

    private String segmentId;
    private List<String> operationNames;
    private int duration;
    private long start;
    private Boolean isError;
    private List<String> traceIds;

    public BasicTrace() {
        this.traceIds = new LinkedList<>();
        this.operationNames = new LinkedList<>();
    }

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public List<String> getOperationName() {
        return operationNames;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public Boolean getError() {
        return isError;
    }

    public void setError(Boolean error) {
        isError = error;
    }

    public List<String> getTraceIds() {
        return traceIds;
    }

    public void setTraceIds(List<String> traceIds) {
        this.traceIds = traceIds;
    }
}

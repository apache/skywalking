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
public class Segment {
    private String segmentId;
    private String applicationCode;
    private String name;
    private SegmentBrief brief;
    private List<Span> spans;
    private boolean isError = false;
    private long duration;

    public Segment() {
        this.brief = new SegmentBrief();
        this.spans = new LinkedList<>();
    }

    public List<Span> getSpans() {
        return spans;
    }

    public void setSpans(List<Span> spans) {
        this.spans = spans;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SegmentBrief getBrief() {
        return brief;
    }

    public void setBrief(SegmentBrief brief) {
        this.brief = brief;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void excludeDuration(long duration){
        this.duration -= duration;
    }

    public void addSpan(Span span){
        this.spans.add(span);
    }
}

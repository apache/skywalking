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

package org.apache.skywalking.e2e.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kezhenxu94
 */
public class Trace {
    private String key;
    private final List<String> endpointNames;
    private int duration;
    private String start;
    private boolean isError;
    private final List<String> traceIds;

    public Trace() {
        this.endpointNames = new ArrayList<>();
        this.traceIds = new ArrayList<>();
    }

    public String getKey() {
        return key;
    }

    public Trace setKey(String key) {
        this.key = key;
        return this;
    }

    public List<String> getEndpointNames() {
        return endpointNames;
    }

    public int getDuration() {
        return duration;
    }

    public Trace setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public String getStart() {
        return start;
    }

    public Trace setStart(String start) {
        this.start = start;
        return this;
    }

    public boolean isError() {
        return isError;
    }

    public Trace setError(boolean error) {
        isError = error;
        return this;
    }

    public List<String> getTraceIds() {
        return traceIds;
    }

    @Override
    public String toString() {
        return "Trace{" +
            "key='" + key + '\'' +
            ", endpointNames=" + endpointNames +
            ", duration=" + duration +
            ", start='" + start + '\'' +
            ", isError=" + isError +
            ", traceIds=" + traceIds +
            '}';
    }
}

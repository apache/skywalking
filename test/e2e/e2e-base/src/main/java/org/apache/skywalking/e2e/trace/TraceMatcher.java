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

import com.google.common.base.Strings;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple matcher to verify the given {@code Trace} is expected
 *
 * @author kezhenxu94
 */
public class TraceMatcher extends AbstractMatcher<Trace> {
    private String key;
    private List<String> endpointNames;
    private String duration;
    private String start;
    private String isError;
    private List<String> traceIds;
    private List<SpanMatcher> spans;

    @Override
    public void verify(final Trace trace) {
        if (Objects.nonNull(getKey())) {
            verifyKey(trace);
        }

        if (Objects.nonNull(getEndpointNames())) {
            verifyEndpointName(trace);
        }

        if (Objects.nonNull(getDuration())) {
            verifyDuration(trace);
        }

        if (Objects.nonNull(getStart())) {
            verifyStart(trace);
        }

        if (Objects.nonNull(getIsError())) {
            verifyIsError(trace);
        }

        if (Objects.nonNull(getTraceIds())) {
            verifyTraceIds(trace);
        }

        if (Objects.nonNull(getSpans())) {
            verifySpans(trace);
        }
    }

    private void verifyKey(Trace trace) {
        final String expected = this.getKey();
        final String actual = trace.getKey();

        doVerify(expected, actual);
    }

    private void verifyEndpointName(Trace trace) {
        assertThat(trace.getEndpointNames()).hasSameSizeAs(getEndpointNames());

        int size = getEndpointNames().size();

        for (int i = 0; i < size; i++) {
            final String expected = getEndpointNames().get(i);
            final String actual = Strings.nullToEmpty(trace.getEndpointNames().get(i));

            doVerify(expected, actual);
        }
    }

    private void verifyDuration(Trace trace) {
        final String expected = this.getDuration();
        final String actual = String.valueOf(trace.getDuration());

        doVerify(expected, actual);
    }

    private void verifyStart(Trace trace) {
        final String expected = this.getStart();
        final String actual = trace.getStart();

        doVerify(expected, actual);
    }

    private void verifyIsError(Trace trace) {
        final String expected = this.getIsError();
        final String actual = Strings.nullToEmpty(String.valueOf(trace.isError()));

        doVerify(expected, actual);
    }

    private void verifyTraceIds(Trace trace) {
        assertThat(trace.getTraceIds()).hasSameSizeAs(getTraceIds());

        int size = getTraceIds().size();

        for (int i = 0; i < size; i++) {
            final String expected = getTraceIds().get(i);
            final String actual = trace.getTraceIds().get(i);

            doVerify(expected, actual);
        }
    }

    private void verifySpans(Trace trace) {
        assertThat(trace.getSpans()).hasSameSizeAs(getSpans());

        int size = getSpans().size();

        for (int i = 0; i < size; i++) {
            getSpans().get(i).verify(trace.getSpans().get(i));
        }
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setEndpointNames(List<String> endpointNames) {
        this.endpointNames = endpointNames;
    }

    public List<String> getEndpointNames() {
        return endpointNames != null ? endpointNames : new ArrayList<>();
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getIsError() {
        return isError;
    }

    public void setIsError(String isError) {
        this.isError = isError;
    }

    public void setTraceIds(List<String> traceIds) {
        this.traceIds = traceIds;
    }

    public List<String> getTraceIds() {
        return traceIds != null ? traceIds : new ArrayList<>();
    }

    public List<SpanMatcher> getSpans() {
        return spans;
    }

    public void setSpans(final List<SpanMatcher> spans) {
        this.spans = spans;
    }

    @Override
    public String toString() {
        return "TraceMatcher{" +
            "key='" + key + '\'' +
            ", endpointNames=" + endpointNames +
            ", duration='" + duration + '\'' +
            ", start='" + start + '\'' +
            ", isError='" + isError + '\'' +
            ", traceIds=" + traceIds +
            ", spans=" + spans +
            '}';
    }
}

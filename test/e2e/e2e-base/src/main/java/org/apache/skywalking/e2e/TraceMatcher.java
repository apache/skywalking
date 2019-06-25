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

package org.apache.skywalking.e2e;

import com.google.common.base.Strings;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple matcher to verify the given {@code Trace} is expected
 *
 * @author kezhenxu94
 */
public class TraceMatcher {
    private static final Pattern NE_MATCHER = Pattern.compile("ne\\s+(?<val>.+)");
    private static final Pattern GT_MATCHER = Pattern.compile("gt\\s+(?<val>.+)");
    private static final Pattern GE_MATCHER = Pattern.compile("ge\\s+(?<val>.+)");
    private static final Pattern NN_MATCHER = Pattern.compile("^not null$");

    private String key;
    private List<String> endpointNames;
    private String duration;
    private String start;
    private String isError;
    private List<String> traceIds;

    public void verify(final Trace trace) {
        verifyKey(trace);

        verifyEndpointName(trace);

        verifyDuration(trace);

        verifyStart(trace);

        verifyIsError(trace);

        verifyTraceIds(trace);
    }

    private void verifyKey(Trace trace) {
        final String expected = this.getKey();
        final String actual = Strings.nullToEmpty(trace.getKey());

        doVerify(expected, actual);
    }

    private void verifyEndpointName(Trace trace) {
        assertThat(trace.getEndpointNames()).hasSize(getEndpointNames().size());

        int size = getEndpointNames().size();

        for (int i = 0; i < size; i++) {
            final String expected = getEndpointNames().get(i);
            final String actual = Strings.nullToEmpty(trace.getEndpointNames().get(i));

            doVerify(expected, actual);
        }
    }

    private void verifyDuration(Trace trace) {
        final String expected = this.getDuration();
        final String actual = Strings.nullToEmpty(String.valueOf(trace.getDuration()));

        doVerify(expected, actual);
    }

    private void verifyStart(Trace trace) {
        final String expected = this.getStart();
        final String actual = Strings.nullToEmpty(String.valueOf(trace.getStart()));

        doVerify(expected, actual);
    }

    private void verifyIsError(Trace trace) {
        final String expected = this.getIsError();
        final String actual = Strings.nullToEmpty(String.valueOf(trace.isError()));

        doVerify(expected, actual);
    }

    private void verifyTraceIds(Trace trace) {
        assertThat(trace.getTraceIds()).hasSize(getTraceIds().size());

        int size = getTraceIds().size();

        for (int i = 0; i < size; i++) {
            final String expected = getTraceIds().get(i);
            final String actual = Strings.nullToEmpty(trace.getTraceIds().get(i));

            doVerify(expected, actual);
        }
    }

    private void doVerify(String expected, String actual) {
        Matcher matcher = NN_MATCHER.matcher(expected);
        if (matcher.find()) {
            assertThat(actual).isNotNull();
        } else {
            matcher = NE_MATCHER.matcher(expected);
            if (matcher.find()) {
                assertThat(actual).isNotEqualTo(matcher.group("val"));
            } else {
                matcher = GT_MATCHER.matcher(expected);
                if (matcher.find()) {
                    assertThat(Double.parseDouble(actual)).isGreaterThan(Double.parseDouble(matcher.group("val")));
                } else {
                    matcher = GE_MATCHER.matcher(expected);
                    if (matcher.find()) {
                        assertThat(Double.parseDouble(actual)).isGreaterThanOrEqualTo(Double.parseDouble(matcher.group("val")));
                    } else {
                        assertThat(actual).isEqualTo(expected);
                    }
                }
            }
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
        return endpointNames;
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
        return traceIds;
    }
}

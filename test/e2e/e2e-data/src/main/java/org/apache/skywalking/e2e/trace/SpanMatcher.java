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
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SpanMatcher extends AbstractMatcher<Span> {
    private String traceId;
    private String segmentId;
    private String spanId;
    private String parentSpanId;
    private String serviceCode;
    private String startTime;
    private String endTime;
    private String endpointName;
    private String type;
    private String peer;
    private String component;
    private String isError;
    private String layer;
    private List<String> tags;

    @Override
    public void verify(final Span span) {
        if (Objects.nonNull(traceId)) {
            String expected = this.getTraceId();
            String actual = span.getTraceId();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(segmentId)) {
            String expected = this.getSegmentId();
            String actual = span.getSegmentId();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(spanId)) {
            String expected = String.valueOf(this.getSpanId());
            String actual = String.valueOf(span.getSpanId());
            doVerify(expected, actual);
        }
        if (Objects.nonNull(parentSpanId)) {
            String expected = String.valueOf(this.getParentSpanId());
            String actual = String.valueOf(span.getParentSpanId());
            doVerify(expected, actual);
        }
        if (Objects.nonNull(serviceCode)) {
            String expected = this.getServiceCode();
            String actual = span.getServiceCode();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(startTime)) {
            String expected = String.valueOf(this.getStartTime());
            String actual = String.valueOf(span.getStartTime());
            doVerify(expected, actual);
        }
        if (Objects.nonNull(endTime)) {
            String expected = String.valueOf(this.getEndTime());
            String actual = String.valueOf(span.getEndTime());
            doVerify(expected, actual);
        }
        if (Objects.nonNull(endpointName)) {
            String expected = this.getEndpointName();
            String actual = span.getEndpointName();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(type)) {
            String expected = this.getType();
            String actual = span.getType();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(peer)) {
            String expected = this.getPeer();
            String actual = span.getPeer();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(component)) {
            String expected = this.getComponent();
            String actual = span.getComponent();
            doVerify(expected, actual);
        }
        if (Objects.nonNull(isError)) {
            String expected = Strings.nullToEmpty(String.valueOf(this.getIsError()));
            String actual = Strings.nullToEmpty(String.valueOf(span.isError()));
            doVerify(expected, actual);
        }
        if (Objects.nonNull(layer)) {
            String expected = this.getLayer();
            String actual = span.getLayer();
            doVerify(expected, actual);
        }

    }

}

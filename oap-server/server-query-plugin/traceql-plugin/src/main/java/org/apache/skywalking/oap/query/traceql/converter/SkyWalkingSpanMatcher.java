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

package org.apache.skywalking.oap.query.traceql.converter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.ALL;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.ERROR;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.HTTP_STATUS_CODE;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.OK;

/**
 * Re-evaluates the TraceQL parameters against one span of a SkyWalking trace, so a search result lists the spans
 * that matched instead of every span of the matched trace, as Tempo does. The conditions mirror what
 * {@code SkyWalkingTraceQLApiHandler} puts into the {@code TraceQueryCondition}: service, instance, endpoint,
 * duration range, error state and tag equality, including {@code http.status_code}.
 */
public final class SkyWalkingSpanMatcher {
    private final TraceQLQueryParams params;
    private final NamingControl namingControl;
    private final Set<String> tags = new HashSet<>();

    /**
     * Compares the raw operation names; for tests. Production code passes the {@link NamingControl}.
     */
    public SkyWalkingSpanMatcher(final TraceQLQueryParams params) {
        this(params, null);
    }

    /**
     * @param namingControl the rules the trace analyzer applied to the endpoint name the storage matched, while the
     *                      span keeps the raw operation name; null compares the raw names
     */
    public SkyWalkingSpanMatcher(final TraceQLQueryParams params, final NamingControl namingControl) {
        this.params = params;
        this.namingControl = namingControl;
        if (params.getTags() != null) {
            for (final Map.Entry<String, String> tag : params.flatTags().entrySet()) {
                tags.add(tag.getKey() + "=" + tag.getValue());
            }
        }
        if (StringUtil.isNotBlank(params.getHttpStatusCode())) {
            tags.add(HTTP_STATUS_CODE + "=" + params.getHttpStatusCode());
        }
    }

    public boolean matches(final Span span) {
        if (differs(params.getServiceName(), span.getServiceCode())
            || differs(params.getServiceInstance(), span.getServiceInstanceName())
            || differs(params.getSpanName(), endpointName(span))) {
            return false;
        }
        final long durationMillis = span.getEndTime() - span.getStartTime();
        if (params.getMinDuration() != null && durationMillis < TimeUnit.MICROSECONDS.toMillis(params.getMinDuration())) {
            return false;
        }
        if (params.getMaxDuration() != null && durationMillis > TimeUnit.MICROSECONDS.toMillis(params.getMaxDuration())) {
            return false;
        }
        if (ERROR.equalsIgnoreCase(params.getStatus()) && !span.isError()) {
            return false;
        }
        if (OK.equalsIgnoreCase(params.getStatus()) && span.isError()) {
            return false;
        }
        if (tags.isEmpty()) {
            return true;
        }
        final Set<String> rendered = new HashSet<>();
        for (final KeyValue tag : span.getTags()) {
            rendered.add(tag.getKey() + "=" + tag.getValue());
        }
        return rendered.containsAll(tags);
    }

    /**
     * A TraceQL value of {@code *} matches anything, as the handler treats it when building the condition.
     */
    private String endpointName(final Span span) {
        return namingControl == null
            ? span.getEndpointName() : namingControl.formatEndpointName(span.getServiceCode(), span.getEndpointName());
    }

    private static boolean differs(final String expected, final String actual) {
        return StringUtil.isNotBlank(expected) && !ALL.equals(expected) && !expected.equals(actual);
    }
}

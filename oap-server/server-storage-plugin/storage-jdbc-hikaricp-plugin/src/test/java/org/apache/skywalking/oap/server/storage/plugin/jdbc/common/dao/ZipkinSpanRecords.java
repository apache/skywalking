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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import zipkin2.Annotation;
import zipkin2.Span;

final class ZipkinSpanRecords {
    private ZipkinSpanRecords() {
    }

    /**
     * The record the Zipkin receiver ({@code SpanForward}) writes for a span.
     */
    static ZipkinSpanRecord of(final Span span) {
        final ZipkinSpanRecord record = new ZipkinSpanRecord();
        record.setTraceId(span.traceId());
        record.setSpanId(span.id());
        record.setParentId(span.parentId());
        record.setName(span.name());
        record.setKind(span.kind().name());
        record.setDuration(span.durationAsLong());
        record.setLocalEndpointServiceName(span.localServiceName());
        record.setLocalEndpointIPV4(span.localEndpoint().ipv4());
        record.setLocalEndpointIPV6(span.localEndpoint().ipv6());
        record.setLocalEndpointPort(span.localEndpoint().portAsInt());
        if (span.remoteEndpoint() != null) {
            record.setRemoteEndpointServiceName(span.remoteServiceName());
            record.setRemoteEndpointIPV4(span.remoteEndpoint().ipv4());
            record.setRemoteEndpointIPV6(span.remoteEndpoint().ipv6());
            record.setRemoteEndpointPort(span.remoteEndpoint().portAsInt());
        }
        record.setTimestamp(span.timestampAsLong());
        record.setTimestampMillis(span.timestampAsLong() / 1000);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(record.getTimestampMillis()));
        final List<String> query = new ArrayList<>();
        if (!span.tags().isEmpty() || !span.annotations().isEmpty()) {
            final JsonObject annotations = new JsonObject();
            for (final Annotation annotation : span.annotations()) {
                annotations.addProperty(Long.toString(annotation.timestamp()), annotation.value());
                query.add(annotation.value());
            }
            final JsonObject tags = new JsonObject();
            for (final Map.Entry<String, String> tag : span.tags().entrySet()) {
                tags.addProperty(tag.getKey(), tag.getValue());
                query.add(tag.getKey());
                query.add(tag.getKey() + "=" + tag.getValue());
            }
            record.setAnnotations(annotations);
            record.setTags(tags);
        }
        record.setQuery(query);
        record.setDebug(Boolean.TRUE.equals(span.debug()) ? 1 : 0);
        record.setShared(Boolean.TRUE.equals(span.shared()) ? 1 : 0);
        return record;
    }
}

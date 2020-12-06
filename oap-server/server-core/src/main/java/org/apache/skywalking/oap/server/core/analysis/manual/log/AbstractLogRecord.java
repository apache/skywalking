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

package org.apache.skywalking.oap.server.core.analysis.manual.log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public abstract class AbstractLogRecord extends Record {

    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String TRACE_ID = "trace_id";
    public static final String TRACE_SEGMENT_ID = "trace_segment_id";
    public static final String SPAN_ID = "span_id";
    public static final String IS_ERROR = "is_error";
    public static final String STATUS_CODE = "status_code";
    public static final String CONTENT_TYPE = "content_type";
    public static final String CONTENT = "content";
    public static final String TIMESTAMP = "timestamp";
    public static final String TAGS = "tags";

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private String serviceId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_INSTANCE_ID)
    private String serviceInstanceId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_ID)
    private String endpointId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_NAME, matchQuery = true)
    private String endpointName;
    @Setter
    @Getter
    @Column(columnName = TRACE_ID)
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = TRACE_SEGMENT_ID)
    private String traceSegmentId;
    @Setter
    @Getter
    @Column(columnName = SPAN_ID)
    private int spanId;
    @Setter
    @Getter
    @Column(columnName = IS_ERROR)
    private int isError;
    @Setter
    @Getter
    @Column(columnName = STATUS_CODE)
    private String statusCode;
    @Setter
    @Getter
    @Column(columnName = CONTENT_TYPE)
    private int contentType = ContentType.NONE.value();
    @Setter
    @Getter
    @Column(columnName = CONTENT, length = 1_000_000)
    private String content;
    @Setter
    @Getter
    @Column(columnName = TIMESTAMP)
    private long timestamp;
    @Setter
    @Getter
    @Column(columnName = TAGS)
    private List<String> tags;

    /**
     * Tags raw data is a duplicate field of {@link #tags}. Some storage don't support array values in a single column.
     * Then, those implementations could use this raw data to generate necessary data structures.
     */
    @Setter
    @Getter
    private List<LogTag> tagsRawData;

    @Override
    public String id() {
        throw new UnexpectedException("AbstractLogRecord doesn't provide id()");
    }

    public static abstract class Builder<T extends AbstractLogRecord> implements StorageBuilder<T> {

        @Override
        public Map<String, Object> data2Map(AbstractLogRecord record) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, record.getServiceId());
            map.put(IS_ERROR, record.getIsError());
            map.put(STATUS_CODE, record.getStatusCode());
            map.put(TIME_BUCKET, record.getTimeBucket());
            map.put(CONTENT_TYPE, record.getContentType());
            map.put(CONTENT, record.getContent());
            map.put(TIMESTAMP, record.getTimestamp());
            // optional
            of(record.getServiceInstanceId())
                .ifPresent(serviceInstanceId -> map.put(SERVICE_INSTANCE_ID, serviceInstanceId));
            ofNullable(record.getEndpointId())
                .ifPresent(endpointId -> map.put(ENDPOINT_ID, endpointId));
            ofNullable(record.getEndpointName())
                .ifPresent(endpointName -> map.put(ENDPOINT_NAME, endpointName));
            ofNullable(record.getTraceId())
                .ifPresent(traceId -> map.put(TRACE_ID, traceId));
            ofNullable(record.getTraceSegmentId())
                .ifPresent(traceSegmentId -> {
                    map.put(TRACE_SEGMENT_ID, traceSegmentId);
                    map.put(SPAN_ID, record.getSpanId());
                });
            map.put(TAGS, record.getTags());
            return map;
        }

        protected void map2Data(T record, Map<String, Object> dbMap) {
            record.setServiceId((String) dbMap.get(SERVICE_ID));
            record.setIsError(((Number) dbMap.get(IS_ERROR)).intValue());
            record.setStatusCode((String) dbMap.get(STATUS_CODE));
            record.setContentType(((Number) dbMap.get(CONTENT_TYPE)).intValue());
            record.setContent((String) dbMap.get(CONTENT));
            record.setTimestamp(((Number) dbMap.get(TIMESTAMP)).longValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            // optional
            ofNullable(dbMap.get(SERVICE_INSTANCE_ID))
                .ifPresent(
                    serviceInstanceId -> record.setServiceInstanceId((String) serviceInstanceId));
            ofNullable(dbMap.get(ENDPOINT_NAME))
                .ifPresent(endpointName -> record.setEndpointName((String) endpointName));
            ofNullable(dbMap.get(ENDPOINT_ID))
                .ifPresent(endpointId -> record.setEndpointId((String) endpointId));
            ofNullable(dbMap.get(TRACE_ID))
                .ifPresent(traceId -> record.setTraceId((String) traceId));
            ofNullable(dbMap.get(TRACE_SEGMENT_ID))
                .ifPresent(traceSegmentId -> record.setTraceSegmentId((String) traceSegmentId));
            ofNullable(dbMap.get(SPAN_ID)).ifPresent(spanId -> record.setSpanId(((Number) spanId).intValue()));
        }
    }
}

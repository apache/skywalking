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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

public abstract class AbstractLogRecord extends Record {

    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String TRACE_ID = "trace_id";
    public static final String TRACE_SEGMENT_ID = "trace_segment_id";
    public static final String SPAN_ID = "span_id";
    public static final String CONTENT_TYPE = "content_type";
    public static final String CONTENT = "content";
    public static final String CONTENT_TYPE_CLASS = "content_type_class";
    public static final String TAGS_RAW_DATA = "tags_raw_data";
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
    @Column(columnName = TRACE_ID, length = 150)
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = TRACE_SEGMENT_ID, length = 150)
    private String traceSegmentId;
    @Setter
    @Getter
    @Column(columnName = SPAN_ID)
    private int spanId;
    @Setter
    @Getter
    @Column(columnName = CONTENT_TYPE, storageOnly = true)
    private int contentType = ContentType.NONE.value();
    @Setter
    @Getter
    @Column(columnName = CONTENT, length = 1_000_000, matchQuery = true, analyzer = Column.AnalyzerType.OAP_LOG_ANALYZER)
    private String content;
    @Setter
    @Getter
    @Column(columnName = TIMESTAMP)
    private long timestamp;

    /**
     * All tag binary data.
     */
    @Setter
    @Getter
    @Column(columnName = TAGS_RAW_DATA, storageOnly = true)
    private byte[] tagsRawData;
    @Setter
    @Getter
    @Column(columnName = TAGS)
    private List<String> tagsInString;

    /**
     * tags is a duplicate field of {@link #tagsInString}. Some storage don't support array values in a single column.
     * Then, those implementations could use this raw data to generate necessary data structures.
     */
    @Setter
    @Getter
    private List<Tag> tags;

    @Override
    public String id() {
        throw new UnexpectedException("AbstractLogRecord doesn't provide id()");
    }

    public static abstract class Builder<T extends AbstractLogRecord> implements StorageHashMapBuilder<T> {

        protected void data2Map(Map<String, Object> map, AbstractLogRecord record) {
            map.put(SERVICE_ID, record.getServiceId());
            map.put(SERVICE_INSTANCE_ID, record.getServiceInstanceId());
            map.put(ENDPOINT_ID, record.getEndpointId());
            map.put(ENDPOINT_NAME, record.getEndpointName());
            map.put(TRACE_ID, record.getTraceId());
            map.put(TRACE_SEGMENT_ID, record.getTraceSegmentId());
            map.put(SPAN_ID, record.getSpanId());
            map.put(TIME_BUCKET, record.getTimeBucket());
            map.put(CONTENT_TYPE, record.getContentType());
            map.put(CONTENT, record.getContent());
            map.put(TIMESTAMP, record.getTimestamp());
            if (CollectionUtils.isEmpty(record.getTagsRawData())) {
                map.put(TAGS_RAW_DATA, Const.EMPTY_STRING);
            } else {
                map.put(TAGS_RAW_DATA, new String(Base64.getEncoder().encode(record.getTagsRawData())));
            }
            map.put(TAGS, record.getTagsInString());
        }

        protected void map2Data(T record, Map<String, Object> dbMap) {
            record.setServiceId((String) dbMap.get(SERVICE_ID));
            record.setServiceInstanceId((String) dbMap.get(SERVICE_INSTANCE_ID));
            record.setEndpointId((String) dbMap.get(ENDPOINT_ID));
            record.setEndpointName((String) dbMap.get(ENDPOINT_NAME));
            record.setTraceId((String) dbMap.get(TRACE_ID));
            record.setTraceSegmentId((String) dbMap.get(TRACE_SEGMENT_ID));
            record.setSpanId(((Number) dbMap.get(SPAN_ID)).intValue());
            record.setContentType(((Number) dbMap.get(CONTENT_TYPE)).intValue());
            record.setContent((String) dbMap.get(CONTENT));
            record.setTimestamp(((Number) dbMap.get(TIMESTAMP)).longValue());
            if (StringUtil.isEmpty((String) dbMap.get(TAGS_RAW_DATA))) {
                record.setTagsRawData(new byte[] {});
            } else {
                // Don't read the tags as they has been in the data binary already.
                record.setTagsRawData(Base64.getDecoder().decode((String) dbMap.get(TAGS_RAW_DATA)));
            }
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
        }
    }
}

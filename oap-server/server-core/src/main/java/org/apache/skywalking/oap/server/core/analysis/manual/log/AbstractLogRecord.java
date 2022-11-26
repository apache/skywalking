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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.LongText;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

public abstract class AbstractLogRecord extends Record {
    public static final String ADDITIONAL_TAG_TABLE = "log_tag";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String TRACE_ID = "trace_id";
    public static final String TRACE_SEGMENT_ID = "trace_segment_id";
    public static final String SPAN_ID = "span_id";
    public static final String CONTENT_TYPE = "content_type";
    public static final String CONTENT = "content";
    public static final String TAGS_RAW_DATA = "tags_raw_data";
    public static final String TIMESTAMP = "timestamp";
    public static final String TAGS = "tags";

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE}, reserveOriginalColumns = true)
    private String serviceId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_INSTANCE_ID, length = 512)
    @BanyanDB.SeriesID(index = 1)
    private String serviceInstanceId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_ID, length = 512)
    private String endpointId;
    @Setter
    @Getter
    @Column(columnName = TRACE_ID, length = 150)
    @BanyanDB.GlobalIndex
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = TRACE_SEGMENT_ID, length = 150)
    @BanyanDB.GlobalIndex
    private String traceSegmentId;
    @Setter
    @Getter
    @Column(columnName = SPAN_ID)
    @BanyanDB.NoIndexing
    private int spanId;
    @Setter
    @Getter
    @Column(columnName = CONTENT_TYPE, storageOnly = true)
    private int contentType = ContentType.NONE.value();
    @Setter
    @Getter
    @Column(columnName = CONTENT, length = 1_000_000)
    @ElasticSearch.MatchQuery(analyzer = ElasticSearch.MatchQuery.AnalyzerType.OAP_LOG_ANALYZER)
    private LongText content;
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
    @Column(columnName = TAGS, indexOnly = true, length = Tag.TAG_LENGTH)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE})
    private List<String> tagsInString;

    @Override
    public String id() {
        throw new UnexpectedException("AbstractLogRecord doesn't provide id()");
    }

    public static abstract class Builder<T extends AbstractLogRecord> implements StorageBuilder<T> {
        protected void map2Data(T record, final Convert2Entity converter) {
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setEndpointId((String) converter.get(ENDPOINT_ID));
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setTraceSegmentId((String) converter.get(TRACE_SEGMENT_ID));
            record.setSpanId(((Number) converter.get(SPAN_ID)).intValue());
            record.setContentType(((Number) converter.get(CONTENT_TYPE)).intValue());
            record.setContent(new LongText((String) converter.get(CONTENT)));
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            record.setTagsRawData(converter.getBytes(TAGS_RAW_DATA));
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
        }

        protected void data2Map(final T record, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, record.getServiceId());
            converter.accept(SERVICE_INSTANCE_ID, record.getServiceInstanceId());
            converter.accept(ENDPOINT_ID, record.getEndpointId());
            converter.accept(TRACE_ID, record.getTraceId());
            converter.accept(TRACE_SEGMENT_ID, record.getTraceSegmentId());
            converter.accept(SPAN_ID, record.getSpanId());
            converter.accept(TIME_BUCKET, record.getTimeBucket());
            converter.accept(CONTENT_TYPE, record.getContentType());
            converter.accept(CONTENT, record.getContent());
            converter.accept(TIMESTAMP, record.getTimestamp());
            converter.accept(TAGS_RAW_DATA, record.getTagsRawData());
            converter.accept(TAGS, record.getTagsInString());
        }
    }
}

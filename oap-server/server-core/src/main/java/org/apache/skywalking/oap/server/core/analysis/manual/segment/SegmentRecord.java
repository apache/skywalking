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

package org.apache.skywalking.oap.server.core.analysis.manual.segment;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.List;

import static org.apache.skywalking.oap.server.core.storage.StorageData.TIME_BUCKET;

@SuperDataset
@Stream(name = SegmentRecord.INDEX_NAME, scopeId = DefaultScopeDefine.SEGMENT, builder = SegmentRecord.Builder.class, processor = RecordStreamProcessor.class)
@SQLDatabase.ExtraColumn4AdditionalEntity(additionalTable = SegmentRecord.ADDITIONAL_TAG_TABLE, parentColumn = TIME_BUCKET)
@BanyanDB.TimestampColumn(SegmentRecord.START_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_TRACE)
public class SegmentRecord extends Record {

    public static final String INDEX_NAME = "segment";
    public static final String ADDITIONAL_TAG_TABLE = "segment_tag";
    public static final String SEGMENT_ID = "segment_id";
    public static final String TRACE_ID = "trace_id";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String START_TIME = "start_time";
    public static final String LATENCY = "latency";
    public static final String IS_ERROR = "is_error";
    public static final String DATA_BINARY = "data_binary";
    public static final String TAGS = "tags";

    @Setter
    @Getter
    @Column(name = SEGMENT_ID, length = 150)
    private String segmentId;
    @Setter
    @Getter
    @Column(name = TRACE_ID, length = 150)
    @ElasticSearch.Routing
    private String traceId;
    @Setter
    @Getter
    @Column(name = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE}, reserveOriginalColumns = true)
    private String serviceId;
    @Setter
    @Getter
    @Column(name = SERVICE_INSTANCE_ID, length = 512)
    @BanyanDB.SeriesID(index = 1)
    private String serviceInstanceId;
    @Setter
    @Getter
    @Column(name = ENDPOINT_ID, length = 512)
    private String endpointId;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME)
    @BanyanDB.NoIndexing
    private long startTime;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @BanyanDB.EnableSort
    @Column(name = LATENCY)
    private int latency;
    @Setter
    @Getter
    @Column(name = IS_ERROR)
    @BanyanDB.SeriesID(index = 2)
    private int isError;
    @Setter
    @Getter
    @Column(name = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;
    @Setter
    @Getter
    @Column(name = TAGS, indexOnly = true, length = Tag.TAG_LENGTH)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE})
    private List<String> tags;

    @Override
    public StorageID id() {
        return new StorageID().append(SEGMENT_ID, segmentId);
    }

    public static class Builder implements StorageBuilder<SegmentRecord> {
        @Override
        public SegmentRecord storage2Entity(final Convert2Entity converter) {
            SegmentRecord record = new SegmentRecord();
            record.setSegmentId((String) converter.get(SEGMENT_ID));
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setEndpointId((String) converter.get(ENDPOINT_ID));
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setLatency(((Number) converter.get(LATENCY)).intValue());
            record.setIsError(((Number) converter.get(IS_ERROR)).intValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setDataBinary(converter.getBytes(DATA_BINARY));
            // Don't read the tags as they have been in the data binary already.
            return record;
        }

        @Override
        public void entity2Storage(final SegmentRecord storageData, final Convert2Storage converter) {
            converter.accept(SEGMENT_ID, storageData.getSegmentId());
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
            converter.accept(ENDPOINT_ID, storageData.getEndpointId());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(LATENCY, storageData.getLatency());
            converter.accept(IS_ERROR, storageData.getIsError());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(DATA_BINARY, storageData.getDataBinary());
            converter.accept(TAGS, storageData.getTags());
        }
    }
}

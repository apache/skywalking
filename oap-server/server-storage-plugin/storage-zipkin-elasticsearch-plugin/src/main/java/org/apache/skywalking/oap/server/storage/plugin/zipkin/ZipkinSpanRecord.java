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

package org.apache.skywalking.oap.server.storage.plugin.zipkin;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@SuperDataset
@Stream(name = ZipkinSpanRecord.INDEX_NAME, scopeId = DefaultScopeDefine.ZIPKIN_SPAN, builder = ZipkinSpanRecord.Builder.class, processor = RecordStreamProcessor.class)
public class ZipkinSpanRecord extends Record {
    public static final String INDEX_NAME = "zipkin_span";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String LATENCY = "latency";
    public static final String IS_ERROR = "is_error";
    public static final String DATA_BINARY = "data_binary";
    public static final String ENCODE = "encode";
    public static final String TAGS = "tags";

    @Setter
    @Getter
    @Column(columnName = TRACE_ID)
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = SPAN_ID)
    private String spanId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_ID, shardingKeyIdx = 0)
    private String serviceId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_INSTANCE_ID, shardingKeyIdx = 1)
    private String serviceInstanceId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_NAME, matchQuery = true)
    private String endpointName;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_ID)
    private String endpointId;
    @Setter
    @Getter
    @Column(columnName = START_TIME)
    private long startTime;
    @Setter
    @Getter
    @Column(columnName = END_TIME)
    private long endTime;
    @Setter
    @Getter
    @Column(columnName = LATENCY)
    private int latency;
    @Setter
    @Getter
    @Column(columnName = IS_ERROR, shardingKeyIdx = 2)
    private int isError;
    @Setter
    @Getter
    @Column(columnName = DATA_BINARY)
    private byte[] dataBinary;
    @Setter
    @Getter
    @Column(columnName = ENCODE)
    private int encode;
    @Setter
    @Getter
    @Column(columnName = TAGS)
    private List<String> tags;

    @Override
    public String id() {
        return traceId + "-" + spanId;
    }

    public static class Builder implements StorageBuilder<ZipkinSpanRecord> {
        @Override
        public ZipkinSpanRecord storage2Entity(final Convert2Entity converter) {
            ZipkinSpanRecord record = new ZipkinSpanRecord();
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setSpanId((String) converter.get(SPAN_ID));
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setEndpointName((String) converter.get(ENDPOINT_NAME));
            record.setEndpointId((String) converter.get(ENDPOINT_ID));
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setEndTime(((Number) converter.get(END_TIME)).longValue());
            record.setLatency(((Number) converter.get(LATENCY)).intValue());
            record.setIsError(((Number) converter.get(IS_ERROR)).intValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setDataBinary(converter.getWith(DATA_BINARY, HashMapConverter.ToEntity.Base64Decoder.INSTANCE));
            record.setEncode(((Number) converter.get(ENCODE)).intValue());
            // Don't read the tags as they have been in the data binary already.
            return record;
        }

        @Override
        public void entity2Storage(final ZipkinSpanRecord storageData, final Convert2Storage converter) {
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(SPAN_ID, storageData.getSpanId());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
            converter.accept(ENDPOINT_NAME, storageData.getEndpointName());
            converter.accept(ENDPOINT_ID, storageData.getEndpointId());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(END_TIME, storageData.getEndTime());
            converter.accept(LATENCY, storageData.getLatency());
            converter.accept(IS_ERROR, storageData.getIsError());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(DATA_BINARY, storageData.getDataBinary());
            converter.accept(ENCODE, storageData.getEncode());
            converter.accept(TAGS, storageData.getTags());
        }
    }
}

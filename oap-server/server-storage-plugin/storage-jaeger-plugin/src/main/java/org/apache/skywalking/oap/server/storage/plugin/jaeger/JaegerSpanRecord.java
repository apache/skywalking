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

package org.apache.skywalking.oap.server.storage.plugin.jaeger;

import java.util.*;
import lombok.*;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Stream(name = JaegerSpanRecord.INDEX_NAME, scopeId = DefaultScopeDefine.JAEGER_SPAN, storage = @Storage(builder = JaegerSpanRecord.Builder.class), processor = RecordStreamProcessor.class)
public class JaegerSpanRecord extends Record {
    public static final String INDEX_NAME = "jaeger_span";
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

    @Setter @Getter @Column(columnName = TRACE_ID) @IDColumn private String traceId;
    @Setter @Getter @Column(columnName = SPAN_ID) @IDColumn private String spanId;
    @Setter @Getter @Column(columnName = SERVICE_ID) @IDColumn private int serviceId;
    @Setter @Getter @Column(columnName = SERVICE_INSTANCE_ID) @IDColumn private int serviceInstanceId;
    @Setter @Getter @Column(columnName = ENDPOINT_NAME, matchQuery = true) @IDColumn private String endpointName;
    @Setter @Getter @Column(columnName = ENDPOINT_ID) @IDColumn private int endpointId;
    @Setter @Getter @Column(columnName = START_TIME) @IDColumn private long startTime;
    @Setter @Getter @Column(columnName = END_TIME) @IDColumn private long endTime;
    @Setter @Getter @Column(columnName = LATENCY) @IDColumn private int latency;
    @Setter @Getter @Column(columnName = IS_ERROR) @IDColumn private int isError;
    @Setter @Getter @Column(columnName = DATA_BINARY) @IDColumn private byte[] dataBinary;
    @Setter @Getter @Column(columnName = ENCODE) @IDColumn private int encode;

    @Override public String id() {
        return traceId + "-" + spanId;
    }

    public static class Builder implements StorageBuilder<JaegerSpanRecord> {

        @Override public Map<String, Object> data2Map(JaegerSpanRecord storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(TRACE_ID, storageData.getTraceId());
            map.put(SPAN_ID, storageData.getSpanId());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
            map.put(ENDPOINT_NAME, storageData.getEndpointName());
            map.put(ENDPOINT_ID, storageData.getEndpointId());
            map.put(START_TIME, storageData.getStartTime());
            map.put(END_TIME, storageData.getEndTime());
            map.put(LATENCY, storageData.getLatency());
            map.put(IS_ERROR, storageData.getIsError());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            if (CollectionUtils.isEmpty(storageData.getDataBinary())) {
                map.put(DATA_BINARY, Const.EMPTY_STRING);
            } else {
                map.put(DATA_BINARY, new String(Base64.getEncoder().encode(storageData.getDataBinary())));
            }
            map.put(ENCODE, storageData.getEncode());
            return map;
        }

        @Override public JaegerSpanRecord map2Data(Map<String, Object> dbMap) {
            JaegerSpanRecord record = new JaegerSpanRecord();
            record.setTraceId((String)dbMap.get(TRACE_ID));
            record.setSpanId((String)dbMap.get(SPAN_ID));
            record.setServiceId(((Number)dbMap.get(SERVICE_ID)).intValue());
            record.setServiceInstanceId(((Number)dbMap.get(SERVICE_INSTANCE_ID)).intValue());
            record.setEndpointName((String)dbMap.get(ENDPOINT_NAME));
            record.setEndpointId(((Number)dbMap.get(ENDPOINT_ID)).intValue());
            record.setStartTime(((Number)dbMap.get(START_TIME)).longValue());
            record.setEndTime(((Number)dbMap.get(END_TIME)).longValue());
            record.setLatency(((Number)dbMap.get(LATENCY)).intValue());
            record.setIsError(((Number)dbMap.get(IS_ERROR)).intValue());
            record.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            if (StringUtil.isEmpty((String)dbMap.get(DATA_BINARY))) {
                record.setDataBinary(new byte[] {});
            } else {
                record.setDataBinary(Base64.getDecoder().decode((String)dbMap.get(DATA_BINARY)));
            }
            record.setEncode(((Number)dbMap.get(ENCODE)).intValue());
            return record;
        }
    }
}

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

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@SuperDataset
@Stream(name = SegmentRecord.INDEX_NAME, scopeId = DefaultScopeDefine.SEGMENT, builder = SegmentRecord.Builder.class, processor = RecordStreamProcessor.class)
public class SegmentRecord extends Record {

    public static final String INDEX_NAME = "segment";
    public static final String SEGMENT_ID = "segment_id";
    public static final String TRACE_ID = "trace_id";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String LATENCY = "latency";
    public static final String IS_ERROR = "is_error";
    public static final String DATA_BINARY = "data_binary";
    public static final String VERSION = "version";
    public static final String TAGS = "tags";

    @Setter
    @Getter
    @Column(columnName = SEGMENT_ID, length = 150)
    private String segmentId;
    @Setter
    @Getter
    @Column(columnName = TRACE_ID, length = 150)
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = TopN.STATEMENT)
    private String statement;
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
    @Column(columnName = LATENCY, dataType = Column.ValueDataType.SAMPLED_RECORD)
    private int latency;
    @Setter
    @Getter
    @Column(columnName = IS_ERROR)
    private int isError;
    @Setter
    @Getter
    @Column(columnName = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;
    @Setter
    @Getter
    @Column(columnName = VERSION, storageOnly = true)
    private int version;
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
    private List<SpanTag> tagsRawData;

    @Override
    public String id() {
        return segmentId;
    }

    public static class Builder implements StorageBuilder<SegmentRecord> {

        @Override
        public Map<String, Object> data2Map(SegmentRecord storageData) {
            storageData.statement = Strings.join(new String[] {
                storageData.endpointName,
                storageData.traceId
            }, " - ");
            Map<String, Object> map = new HashMap<>();
            map.put(SEGMENT_ID, storageData.getSegmentId());
            map.put(TRACE_ID, storageData.getTraceId());
            map.put(TopN.STATEMENT, storageData.getStatement());
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
            map.put(VERSION, storageData.getVersion());
            map.put(TAGS, storageData.getTags());
            return map;
        }

        @Override
        public SegmentRecord map2Data(Map<String, Object> dbMap) {
            SegmentRecord record = new SegmentRecord();
            record.setSegmentId((String) dbMap.get(SEGMENT_ID));
            record.setTraceId((String) dbMap.get(TRACE_ID));
            record.setStatement((String) dbMap.get(TopN.STATEMENT));
            record.setServiceId((String) dbMap.get(SERVICE_ID));
            record.setServiceInstanceId((String) dbMap.get(SERVICE_INSTANCE_ID));
            record.setEndpointName((String) dbMap.get(ENDPOINT_NAME));
            record.setEndpointId((String) dbMap.get(ENDPOINT_ID));
            record.setStartTime(((Number) dbMap.get(START_TIME)).longValue());
            record.setEndTime(((Number) dbMap.get(END_TIME)).longValue());
            record.setLatency(((Number) dbMap.get(LATENCY)).intValue());
            record.setIsError(((Number) dbMap.get(IS_ERROR)).intValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            if (StringUtil.isEmpty((String) dbMap.get(DATA_BINARY))) {
                record.setDataBinary(new byte[] {});
            } else {
                record.setDataBinary(Base64.getDecoder().decode((String) dbMap.get(DATA_BINARY)));
            }
            record.setVersion(((Number) dbMap.get(VERSION)).intValue());
            // Don't read the tags as they has been in the data binary already.
            return record;
        }
    }
}

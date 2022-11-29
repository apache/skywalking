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

package org.apache.skywalking.oap.server.core.analysis.manual.spanattach;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SPAN_ATTACHED_EVENT;

@Setter
@Getter
@ScopeDeclaration(id = SPAN_ATTACHED_EVENT, name = "SpanAttachedEvent")
@Stream(name = SpanAttachedEventRecord.INDEX_NAME, scopeId = SPAN_ATTACHED_EVENT, builder = SpanAttachedEventRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(SpanAttachedEventRecord.TIMESTAMP)
public class SpanAttachedEventRecord extends Record {

    public static final String INDEX_NAME = "span_attached_event_record";
    public static final String START_TIME_SECOND = "start_time_second";
    public static final String START_TIME_NANOS = "start_time_nanos";
    public static final String EVENT = "event";
    public static final String END_TIME_SECOND = "end_time_second";
    public static final String END_TIME_NANOS = "end_time_nanos";
    public static final String TRACE_REF_TYPE = "trace_ref_type";
    public static final String TRACE_ID = "trace_id";
    public static final String TRACE_SEGMENT_ID = "trace_segment_id";
    public static final String TRACE_SPAN_ID = "trace_span_id";
    public static final String DATA_BINARY = "data_binary";
    public static final String TIMESTAMP = "timestamp";

    @Column(columnName = START_TIME_SECOND)
    private long startTimeSecond;
    @Column(columnName = START_TIME_NANOS)
    private int startTimeNanos;
    @Column(columnName = EVENT)
    @BanyanDB.SeriesID(index = 0)
    private String event;
    @Column(columnName = END_TIME_SECOND)
    private long endTimeSecond;
    @Column(columnName = END_TIME_NANOS)
    private int endTimeNanos;
    @Column(columnName = TRACE_REF_TYPE)
    private int traceRefType;
    @Column(columnName = TRACE_ID)
    @BanyanDB.GlobalIndex
    private String traceId;
    @Column(columnName = TRACE_SEGMENT_ID)
    private String traceSegmentId;
    @Column(columnName = TRACE_SPAN_ID)
    private String traceSpanId;
    @Column(columnName = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;
    @Setter
    @Getter
    @Column(columnName = TIMESTAMP)
    private long timestamp;

    @Override
    public String id() {
        return traceSegmentId + Const.ID_CONNECTOR + startTimeSecond + Const.ID_CONNECTOR + startTimeNanos + Const.ID_CONNECTOR + event;
    }

    public static class Builder implements StorageBuilder<SpanAttachedEventRecord> {
        @Override
        public SpanAttachedEventRecord storage2Entity(Convert2Entity converter) {
            final SpanAttachedEventRecord record = new SpanAttachedEventRecord();
            record.setStartTimeSecond(((Number) converter.get(START_TIME_SECOND)).longValue());
            record.setStartTimeNanos(((Number) converter.get(START_TIME_NANOS)).intValue());
            record.setEvent((String) converter.get(EVENT));
            record.setEndTimeSecond(((Number) converter.get(END_TIME_SECOND)).longValue());
            record.setEndTimeNanos(((Number) converter.get(END_TIME_NANOS)).intValue());
            record.setTraceRefType(((Number) converter.get(TRACE_REF_TYPE)).intValue());
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setTraceSegmentId((String) converter.get(TRACE_SEGMENT_ID));
            record.setTraceSpanId((String) converter.get(TRACE_SPAN_ID));
            record.setDataBinary(converter.getBytes(DATA_BINARY));
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(SpanAttachedEventRecord entity, Convert2Storage converter) {
            converter.accept(START_TIME_SECOND, entity.getStartTimeSecond());
            converter.accept(START_TIME_NANOS, entity.getStartTimeNanos());
            converter.accept(EVENT, entity.getEvent());
            converter.accept(END_TIME_SECOND, entity.getEndTimeSecond());
            converter.accept(END_TIME_NANOS, entity.getEndTimeNanos());
            converter.accept(TRACE_REF_TYPE, entity.getTraceRefType());
            converter.accept(TRACE_ID, entity.getTraceId());
            converter.accept(TRACE_SEGMENT_ID, entity.getTraceSegmentId());
            converter.accept(TRACE_SPAN_ID, entity.getTraceSpanId());
            converter.accept(DATA_BINARY, entity.getDataBinary());
            converter.accept(TIMESTAMP, entity.getTimestamp());
        }
    }
}

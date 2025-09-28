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

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SPAN_ATTACHED_EVENT;

@Setter
@Getter
@ScopeDeclaration(id = SPAN_ATTACHED_EVENT, name = "SpanAttachedEvent")
@Stream(name = SpanAttachedEventRecord.INDEX_NAME, scopeId = SPAN_ATTACHED_EVENT, builder = SpanAttachedEventRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(SpanAttachedEventRecord.TIMESTAMP)
@BanyanDB.Trace.TraceIdColumn(SpanAttachedEventRecord.RELATED_TRACE_ID)
@BanyanDB.Group(traceGroup = BanyanDB.TraceGroup.ZIPKIN_TRACE)
public class SpanAttachedEventRecord extends Record implements BanyanDBTrace, BanyanDBTrace.MergeTable {

    public static final String INDEX_NAME = "span_attached_event_record";
    public static final String START_TIME_SECOND = "start_time_second";
    public static final String START_TIME_NANOS = "start_time_nanos";
    public static final String EVENT = "event";
    public static final String END_TIME_SECOND = "end_time_second";
    public static final String END_TIME_NANOS = "end_time_nanos";
    public static final String TRACE_REF_TYPE = "trace_ref_type";
    public static final String RELATED_TRACE_ID = "related_trace_id";
    public static final String TRACE_SEGMENT_ID = "trace_segment_id";
    public static final String TRACE_SPAN_ID = "trace_span_id";
    public static final String DATA_BINARY = "data_binary";
    public static final String TIMESTAMP = "timestamp";

    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME_SECOND)
    private long startTimeSecond;
    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME_NANOS)
    private int startTimeNanos;
    @Column(name = EVENT)
    private String event;
    @Column(name = END_TIME_SECOND)
    private long endTimeSecond;
    @Column(name = END_TIME_NANOS)
    private int endTimeNanos;
    @Column(name = TRACE_REF_TYPE)
    private int traceRefType;
    @Column(name = RELATED_TRACE_ID)
    private String relatedTraceId;
    @Column(name = TRACE_SEGMENT_ID)
    private String traceSegmentId;
    @Column(name = TRACE_SPAN_ID)
    private String traceSpanId;
    @Column(name = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP)
    private long timestamp;

    @Override
    public StorageID id() {
        return new StorageID()
            .append(TRACE_SEGMENT_ID, traceSegmentId)
            .append(START_TIME_SECOND, startTimeSecond)
            .append(START_TIME_NANOS, startTimeNanos)
            .append(EVENT, event);
    }

    @Override
    public SpanWrapper getSpanWrapper() {
        SpanWrapper.Builder builder = SpanWrapper.newBuilder();
        builder.setSpan(ByteString.copyFrom(dataBinary));
        builder.setSource(Source.ZIPKIN_EVENT);
        return builder.build();
    }

    @Override
    public String getMergeTableName() {
        return ZipkinSpanRecord.INDEX_NAME;
    }

    @Override
    public String getMergeTraceIdColumnName() {
        return ZipkinSpanRecord.TRACE_ID;
    }

    @Override
    public String getTraceIdColumnValue() {
        return relatedTraceId;
    }

    @Override
    public String getMergeTimestampColumnName() {
        return ZipkinSpanRecord.TIMESTAMP_MILLIS;
    }

    @Override
    public long getTimestampColumnValue() {
        return timestamp;
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
            record.setRelatedTraceId((String) converter.get(RELATED_TRACE_ID));
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
            converter.accept(RELATED_TRACE_ID, entity.getRelatedTraceId());
            converter.accept(TRACE_SEGMENT_ID, entity.getTraceSegmentId());
            converter.accept(TRACE_SPAN_ID, entity.getTraceSpanId());
            converter.accept(DATA_BINARY, entity.getDataBinary());
            converter.accept(TIMESTAMP, entity.getTimestamp());
        }
    }
}

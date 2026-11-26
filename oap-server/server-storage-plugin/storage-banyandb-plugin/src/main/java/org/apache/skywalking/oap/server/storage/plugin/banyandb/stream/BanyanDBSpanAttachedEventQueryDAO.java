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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.library.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The events are spans of the trace they attach to, in the {@code segment} or {@code zipkin_span} trace, where
 * {@code @BanyanDB.Trace.StoreAsAttachmentOf} writes them: no stream of their own exists, and their spans carry only the event
 * itself, from which the record is rebuilt.
 */
public class BanyanDBSpanAttachedEventQueryDAO extends AbstractBanyanDBDAO implements ISpanAttachedEventQueryDAO {
    private static final Comparator<EventColumns> BY_START_TIME =
        Comparator.comparingLong((EventColumns columns) -> columns.event.getStartTime().getSeconds())
                  .thenComparingInt(columns -> columns.event.getStartTime().getNanos());

    public BanyanDBSpanAttachedEventQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<SWSpanAttachedEventRecord> querySWSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) throws IOException {
        final List<SWSpanAttachedEventRecord> records = new ArrayList<>();
        for (final EventColumns event : queryEvents(
            SegmentRecord.INDEX_NAME, SegmentRecord.TRACE_ID, Source.SKYWALKING_EVENT, traceIds, duration)) {
            records.add(new SWSpanAttachedEventRecord.Builder().storage2Entity(event));
        }
        return records;
    }

    @Override
    public List<SpanAttachedEventRecord> queryZKSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) throws IOException {
        final List<SpanAttachedEventRecord> records = new ArrayList<>();
        for (final EventColumns event : queryEvents(
            ZipkinSpanRecord.INDEX_NAME, ZipkinSpanRecord.TRACE_ID, Source.ZIPKIN_EVENT, traceIds, duration)) {
            records.add(new SpanAttachedEventRecord.Builder().storage2Entity(event));
        }
        return records;
    }

    /**
     * Every event of the traces, in start-time order to the nanosecond, as the other storages return them. The limit
     * counts traces, and a trace comes back with all its spans. The duration only tells BanyanDB where to look: it
     * does not clip a lookup by trace id.
     */
    private List<EventColumns> queryEvents(String traceModelName, String traceIdColumn, Source eventSource,
                                           List<String> traceIds, @Nullable Duration duration) throws IOException {
        if (CollectionUtils.isEmpty(traceIds)) {
            return Collections.emptyList();
        }
        final boolean isColdStage = duration != null && duration.isColdStage();
        final TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, traceModelName, getTimestampRange(duration),
            Conditions.create().in(traceIdColumn, traceIds).limit(traceIds.size()));
        final List<EventColumns> events = new ArrayList<>();
        for (final BanyandbTrace.Trace trace : resp.getTraces()) {
            for (final BanyandbTrace.Span span : trace.getSpansList()) {
                final SpanWrapper wrapper = SpanWrapper.parseFrom(span.getSpan());
                if (wrapper.getSource() == eventSource) {
                    events.add(new EventColumns(
                        SpanAttachedEvent.parseFrom(wrapper.getSpan()), wrapper.getSpan().toByteArray()));
                }
            }
        }
        events.sort(BY_START_TIME);
        return events;
    }

    /**
     * The columns of an event's record, filled from the event the way the trace receiver fills them. The native and
     * the Zipkin record share their column names.
     */
    @RequiredArgsConstructor
    private static final class EventColumns implements Convert2Entity {
        private final SpanAttachedEvent event;
        private final byte[] dataBinary;

        @Override
        public Object get(String fieldName) {
            switch (fieldName) {
                case SWSpanAttachedEventRecord.START_TIME_SECOND:
                    return event.getStartTime().getSeconds();
                case SWSpanAttachedEventRecord.START_TIME_NANOS:
                    return event.getStartTime().getNanos();
                case SWSpanAttachedEventRecord.EVENT:
                    return event.getEvent();
                case SWSpanAttachedEventRecord.END_TIME_SECOND:
                    return event.getEndTime().getSeconds();
                case SWSpanAttachedEventRecord.END_TIME_NANOS:
                    return event.getEndTime().getNanos();
                case SWSpanAttachedEventRecord.TRACE_REF_TYPE:
                    return event.getTraceContext().getTypeValue();
                case SWSpanAttachedEventRecord.RELATED_TRACE_ID:
                    return event.getTraceContext().getTraceId();
                case SWSpanAttachedEventRecord.TRACE_SEGMENT_ID:
                    return event.getTraceContext().getTraceSegmentId();
                case SWSpanAttachedEventRecord.TRACE_SPAN_ID:
                    return event.getTraceContext().getSpanId();
                case SWSpanAttachedEventRecord.TIMESTAMP:
                    return TimeUnit.SECONDS.toMillis(event.getStartTime().getSeconds())
                        + TimeUnit.NANOSECONDS.toMillis(event.getStartTime().getNanos());
                default:
                    return null;
            }
        }

        @Override
        public byte[] getBytes(String fieldName) {
            return SWSpanAttachedEventRecord.DATA_BINARY.equals(fieldName) ? dataBinary : null;
        }
    }
}

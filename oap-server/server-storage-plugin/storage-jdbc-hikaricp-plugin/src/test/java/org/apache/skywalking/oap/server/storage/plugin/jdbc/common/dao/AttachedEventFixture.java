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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.joda.time.DateTime;

/**
 * The time base and the span attached events of the native and the Zipkin trace fixtures, and how OAP stores an
 * event.
 */
final class AttachedEventFixture {
    /** Today's local midnight, so the fixture is written to yesterday's and today's tables, within the TTL. */
    static final long MIDNIGHT = JDBCQueryITSupport.MIDNIGHT;
    static final long MIDNIGHT_SECOND = MIDNIGHT / 1000;
    /** A minute either side of midnight, the duration every query of the suite passes. */
    static final Duration DURATION = duration(MIDNIGHT + 59_000L, 119_000L);
    static final long START_SECOND = DURATION.getStartTimestamp() / 1000;
    static final long END_SECOND = DURATION.getEndTimestamp() / 1000;

    private AttachedEventFixture() {
    }

    /**
     * The duration {@code ZipkinQueryHandler} builds from a request's {@code endTs} and {@code lookback}.
     */
    static Duration duration(final long endTs, final long lookback) {
        final Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        duration.setStart(new DateTime(endTs - lookback).toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(new DateTime(endTs).toString("yyyy-MM-dd HHmmss"));
        return duration;
    }

    /**
     * An event as rover reports it, lasting 2 ms, against the trace context the traffic carried.
     *
     * @param tags keys and values, one after the other
     */
    static SpanAttachedEvent.Builder event(final SpanAttachedEvent.SpanReferenceType type, final String traceId,
                                           final String segmentId, final String spanId, final long startSecond,
                                           final int startNanos, final String name, final String... tags) {
        final long endNanos = startNanos + 2_000_000L;
        final SpanAttachedEvent.Builder event = SpanAttachedEvent.newBuilder()
            .setStartTime(Instant.newBuilder().setSeconds(startSecond).setNanos(startNanos))
            .setEndTime(Instant.newBuilder()
                               .setSeconds(startSecond + endNanos / 1_000_000_000L)
                               .setNanos((int) (endNanos % 1_000_000_000L)))
            .setEvent(name)
            .setTraceContext(SpanAttachedEvent.SpanReference.newBuilder()
                                                            .setType(type)
                                                            .setTraceId(traceId)
                                                            .setTraceSegmentId(segmentId)
                                                            .setSpanId(spanId));
        for (int i = 0; i < tags.length; i += 2) {
            event.addTags(KeyStringValuePair.newBuilder().setKey(tags[i]).setValue(tags[i + 1]));
        }
        return event;
    }

    /**
     * The record {@code SpanAttachedEventReportServiceHandler} writes for an event of a SkyWalking trace context.
     */
    static SWSpanAttachedEventRecord skyWalkingRecord(final SpanAttachedEvent event) {
        final SWSpanAttachedEventRecord record = new SWSpanAttachedEventRecord();
        record.setStartTimeSecond(event.getStartTime().getSeconds());
        record.setStartTimeNanos(event.getStartTime().getNanos());
        record.setEvent(event.getEvent());
        record.setEndTimeSecond(event.getEndTime().getSeconds());
        record.setEndTimeNanos(event.getEndTime().getNanos());
        record.setTraceRefType(event.getTraceContext().getTypeValue());
        record.setRelatedTraceId(event.getTraceContext().getTraceId());
        record.setTraceSegmentId(event.getTraceContext().getTraceSegmentId());
        record.setTraceSpanId(event.getTraceContext().getSpanId());
        record.setDataBinary(event.toByteArray());
        final long timestamp = timestampOf(event);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setTimestamp(timestamp);
        return record;
    }

    /**
     * The record {@code SpanAttachedEventReportServiceHandler} writes for an event of a Zipkin trace context.
     */
    static SpanAttachedEventRecord zipkinRecord(final SpanAttachedEvent event) {
        final SpanAttachedEventRecord record = new SpanAttachedEventRecord();
        record.setStartTimeSecond(event.getStartTime().getSeconds());
        record.setStartTimeNanos(event.getStartTime().getNanos());
        record.setEvent(event.getEvent());
        record.setEndTimeSecond(event.getEndTime().getSeconds());
        record.setEndTimeNanos(event.getEndTime().getNanos());
        record.setTraceRefType(event.getTraceContext().getTypeValue());
        record.setRelatedTraceId(event.getTraceContext().getTraceId());
        record.setTraceSegmentId(event.getTraceContext().getTraceSegmentId());
        record.setTraceSpanId(event.getTraceContext().getSpanId());
        record.setDataBinary(event.toByteArray());
        final long timestamp = timestampOf(event);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setTimestamp(timestamp);
        return record;
    }

    private static long timestampOf(final SpanAttachedEvent event) {
        return TimeUnit.SECONDS.toMillis(event.getStartTime().getSeconds())
            + TimeUnit.NANOSECONDS.toMillis(event.getStartTime().getNanos());
    }

    /**
     * Every column the records' storage builder stores but the timestamp, so records compare whole: the JDBC DAO
     * does not read the timestamp back, and no caller uses it.
     */
    static <T extends StorageData> List<Map<String, Object>> stored(final StorageBuilder<T> builder,
                                                                    final List<T> records) {
        final List<Map<String, Object>> stored = new ArrayList<>(records.size());
        for (final T record : records) {
            final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
            builder.entity2Storage(record, columns);
            final Map<String, Object> read = new TreeMap<>(columns.obtain());
            read.remove(SpanAttachedEventRecord.TIMESTAMP);
            stored.add(read);
        }
        return stored;
    }
}

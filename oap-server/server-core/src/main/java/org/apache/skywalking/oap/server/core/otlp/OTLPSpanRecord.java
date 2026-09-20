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

package org.apache.skywalking.oap.server.core.otlp;

import com.google.protobuf.ByteString;
import java.util.List;
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
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.storage.StorageData.TIME_BUCKET;

/**
 * One OTLP span stored natively: the columns are the search index, {@link #dataBinary} is a single-span
 * {@code ResourceSpans} message holding the resource, the scope and the span exactly as the SDK sent them.
 * The read path serves {@code dataBinary}; the columns are never converted back into a span, so no field of
 * the wire message is lost the way the Zipkin conversion loses attribute types, events and links.
 *
 * <p>{@code server-core} keeps no dependency on the OTLP protobuf classes: the record carries {@code byte[]}
 * and decoding happens in the query plugin, the same split {@code OTLPSpanReader} enforces.
 */
@SuperDataset
@Stream(name = OTLPSpanRecord.INDEX_NAME, scopeId = DefaultScopeDefine.OTLP_SPAN,
    builder = OTLPSpanRecord.Builder.class, processor = RecordStreamProcessor.class)
@SQLDatabase.ExtraColumn4AdditionalEntity(additionalTable = OTLPSpanRecord.ADDITIONAL_TAG_TABLE, parentColumn = TIME_BUCKET)
@BanyanDB.TimestampColumn(OTLPSpanRecord.START_TIME)
@BanyanDB.Trace.TraceIdColumn(OTLPSpanRecord.TRACE_ID)
@BanyanDB.Trace.SpanIdColumn(OTLPSpanRecord.SPAN_ID)
// The BanyanDB trace model answers an equality on an int tag only through an index rule; string tags are
// scanned. kind and status_code are the int filters TraceQL exposes, so both sit in every rule.
@BanyanDB.Trace.IndexRule(name = OTLPSpanRecord.START_TIME, columns = {
    OTLPSpanRecord.SERVICE_NAME,
    OTLPSpanRecord.STATUS_CODE,
    OTLPSpanRecord.KIND,
}, orderByColumn = OTLPSpanRecord.START_TIME)
@BanyanDB.Trace.IndexRule(name = OTLPSpanRecord.DURATION, columns = {
    OTLPSpanRecord.SERVICE_NAME,
    OTLPSpanRecord.STATUS_CODE,
    OTLPSpanRecord.KIND,
}, orderByColumn = OTLPSpanRecord.DURATION)
@BanyanDB.Group(traceGroup = BanyanDB.TraceGroup.OTLP_TRACE)
public class OTLPSpanRecord extends Record implements BanyanDBTrace {
    public static final String INDEX_NAME = "otlp_span";
    public static final String ADDITIONAL_TAG_TABLE = "otlp_span_tag";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String PARENT_SPAN_ID = "parent_span_id";
    public static final String SERVICE_NAME = "service_name";
    public static final String SERVICE_INSTANCE = "service_instance";
    public static final String SCOPE_NAME = "scope_name";
    public static final String NAME = "name";
    public static final String KIND = "kind";
    public static final String STATUS_CODE = "status_code";
    public static final String PEER_SERVICE = "peer_service";
    public static final String START_TIME = "start_time";
    public static final String DURATION = "duration";
    public static final String TAGS = "tags";
    public static final String DATA_BINARY = "data_binary";
    /**
     * Resource attribute keys {@link #SERVICE_NAME} is resolved from, first present wins. The receiver fills the
     * column with it and the TraceQL datasource re-resolves it the same way when it matches spans in memory.
     */
    public static final List<String> SERVICE_NAME_RESOURCE_KEYS = List.of(
        "service.name", "faas.name", "k8s.deployment.name", "process.executable.name");
    /**
     * Resource attribute keys {@link #SERVICE_INSTANCE} is resolved from, first present wins.
     */
    public static final List<String> SERVICE_INSTANCE_RESOURCE_KEYS = List.of("service.instance.id", "service.instance");
    /**
     * The span attribute {@link #PEER_SERVICE} is copied from, per the OpenTelemetry semantic conventions.
     */
    public static final String PEER_SERVICE_ATTRIBUTE = "peer.service";

    /**
     * 32 lowercase hex characters.
     */
    @Setter
    @Getter
    @Column(name = TRACE_ID)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE}, reserveOriginalColumns = true)
    @ElasticSearch.Routing
    @ElasticSearch.EnableDocValues
    private String traceId;
    /**
     * 16 lowercase hex characters.
     */
    @Setter
    @Getter
    @Column(name = SPAN_ID)
    private String spanId;
    /**
     * Lowercase hex, empty for a root span.
     */
    @Setter
    @Getter
    @Column(name = PARENT_SPAN_ID, storageOnly = true)
    private String parentSpanId;
    @Setter
    @Getter
    @Column(name = SERVICE_NAME)
    private String serviceName;
    @Setter
    @Getter
    @Column(name = SERVICE_INSTANCE)
    private String serviceInstance;
    @Setter
    @Getter
    @Column(name = SCOPE_NAME)
    private String scopeName;
    @Setter
    @Getter
    @Column(name = NAME)
    private String name;
    /**
     * The OTLP {@code SpanKind} enum number, 0 UNSPECIFIED to 5 CONSUMER.
     */
    @Setter
    @Getter
    @Column(name = KIND)
    private int kind;
    /**
     * The OTLP {@code StatusCode} enum number, 0 UNSET, 1 OK, 2 ERROR.
     */
    @Setter
    @Getter
    @Column(name = STATUS_CODE)
    private int statusCode;
    @Setter
    @Getter
    @Column(name = PEER_SERVICE)
    private String peerService;
    /**
     * Milliseconds since the epoch, from {@code start_time_unix_nano}.
     */
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME)
    private long startTime;
    /**
     * Nanoseconds, {@code end_time_unix_nano - start_time_unix_nano}.
     */
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @BanyanDB.EnableSort
    @Column(name = DURATION)
    private long duration;
    /**
     * {@code key=value} of every resource and span attribute, the equality-search index. The typed values stay
     * in {@link #dataBinary}.
     */
    @Setter
    @Getter
    @Column(name = TAGS, indexOnly = true, length = Tag.TAG_LENGTH)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE})
    private List<String> tags;
    /**
     * A serialized single-span {@code ResourceSpans}.
     */
    @Setter
    @Getter
    @Column(name = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;

    @Override
    public StorageID id() {
        return new StorageID().append(TRACE_ID, traceId).append(SPAN_ID, spanId);
    }

    @Override
    public SpanWrapper getSpanWrapper() {
        return SpanWrapper.newBuilder()
                          .setSpan(ByteString.copyFrom(dataBinary))
                          .setSource(Source.OTLP)
                          .build();
    }

    public static class Builder implements StorageBuilder<OTLPSpanRecord> {
        @Override
        public OTLPSpanRecord storage2Entity(final Convert2Entity converter) {
            final OTLPSpanRecord record = new OTLPSpanRecord();
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setSpanId((String) converter.get(SPAN_ID));
            record.setParentSpanId((String) converter.get(PARENT_SPAN_ID));
            record.setServiceName((String) converter.get(SERVICE_NAME));
            record.setServiceInstance((String) converter.get(SERVICE_INSTANCE));
            record.setScopeName((String) converter.get(SCOPE_NAME));
            record.setName((String) converter.get(NAME));
            if (converter.get(KIND) != null) {
                record.setKind(((Number) converter.get(KIND)).intValue());
            }
            if (converter.get(STATUS_CODE) != null) {
                record.setStatusCode(((Number) converter.get(STATUS_CODE)).intValue());
            }
            record.setPeerService((String) converter.get(PEER_SERVICE));
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setDuration(((Number) converter.get(DURATION)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setDataBinary(converter.getBytes(DATA_BINARY));
            // tags are the index only: every attribute is inside dataBinary already.
            return record;
        }

        @Override
        public void entity2Storage(final OTLPSpanRecord storageData, final Convert2Storage converter) {
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(SPAN_ID, storageData.getSpanId());
            converter.accept(PARENT_SPAN_ID, storageData.getParentSpanId());
            converter.accept(SERVICE_NAME, storageData.getServiceName());
            converter.accept(SERVICE_INSTANCE, storageData.getServiceInstance());
            converter.accept(SCOPE_NAME, storageData.getScopeName());
            converter.accept(NAME, storageData.getName());
            converter.accept(KIND, storageData.getKind());
            converter.accept(STATUS_CODE, storageData.getStatusCode());
            converter.accept(PEER_SERVICE, storageData.getPeerService());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(DURATION, storageData.getDuration());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(TAGS, storageData.getTags());
            converter.accept(DATA_BINARY, storageData.getDataBinary());
        }
    }
}

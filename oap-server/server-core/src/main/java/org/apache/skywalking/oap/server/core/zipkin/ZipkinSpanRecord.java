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

package org.apache.skywalking.oap.server.core.zipkin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin2.Endpoint;
import zipkin2.Span;

import java.util.List;
import java.util.Map;
import zipkin2.codec.SpanBytesEncoder;

import static org.apache.skywalking.oap.server.core.storage.StorageData.TIME_BUCKET;

@SuperDataset
@Stream(name = ZipkinSpanRecord.INDEX_NAME, scopeId = DefaultScopeDefine.ZIPKIN_SPAN, builder = ZipkinSpanRecord.Builder.class, processor = RecordStreamProcessor.class)
@SQLDatabase.ExtraColumn4AdditionalEntity(additionalTable = ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE, parentColumn = TIME_BUCKET)
@BanyanDB.TimestampColumn(ZipkinSpanRecord.TIMESTAMP_MILLIS)
@BanyanDB.Trace.TraceIdColumn(ZipkinSpanRecord.TRACE_ID)
@BanyanDB.Trace.IndexRule(name = ZipkinSpanRecord.TIMESTAMP_MILLIS, columns = {
    ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME,
}, orderByColumn = ZipkinSpanRecord.TIMESTAMP_MILLIS)
@BanyanDB.Group(traceGroup = BanyanDB.TraceGroup.ZIPKIN_TRACE)
public class ZipkinSpanRecord extends Record implements BanyanDBTrace {
    private static final Gson GSON = new Gson();
    public static final int QUERY_LENGTH = 256;
    public static final String INDEX_NAME = "zipkin_span";
    public static final String ADDITIONAL_QUERY_TABLE = "zipkin_query";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String PARENT_ID = "parent_id";
    public static final String NAME = "name";
    public static final String DURATION = "duration";
    public static final String KIND = "kind";
    public static final String TIMESTAMP_MILLIS = "timestamp_millis";
    public static final String TIMESTAMP = "timestamp";
    public static final String LOCAL_ENDPOINT_SERVICE_NAME = "local_endpoint_service_name";
    public static final String LOCAL_ENDPOINT_IPV4 = "local_endpoint_ipv4";
    public static final String LOCAL_ENDPOINT_IPV6 = "local_endpoint_ipv6";
    public static final String LOCAL_ENDPOINT_PORT = "local_endpoint_port";
    public static final String REMOTE_ENDPOINT_SERVICE_NAME = "remote_endpoint_service_name";
    public static final String REMOTE_ENDPOINT_IPV4 = "remote_endpoint_ipv4";
    public static final String REMOTE_ENDPOINT_IPV6 = "remote_endpoint_ipv6";
    public static final String REMOTE_ENDPOINT_PORT = "remote_endpoint_port";
    public static final String ANNOTATIONS = "annotations";
    public static final String TAGS = "tags";
    public static final String DEBUG = "debug";
    public static final String SHARED = "shared";
    public static final String QUERY = "query";

    @Setter
    @Getter
    @Column(name = TRACE_ID)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_QUERY_TABLE}, reserveOriginalColumns = true)
    @ElasticSearch.Routing
    @ElasticSearch.EnableDocValues
    private String traceId;
    @Setter
    @Getter
    @Column(name = SPAN_ID)
    private String spanId;
    @Setter
    @Getter
    @Column(name = PARENT_ID)
    private String parentId;
    @Setter
    @Getter
    @Column(name = NAME)
    private String name;
    @Setter
    @Getter
    @Column(name = DURATION)
    private long duration;
    @Setter
    @Getter
    @Column(name = KIND)
    private String kind;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP_MILLIS)
    private long timestampMillis;
    @Setter
    @Getter
    @Column(name = TIMESTAMP)
    @BanyanDB.NoIndexing
    private long timestamp;
    @Setter
    @Getter
    @Column(name = LOCAL_ENDPOINT_SERVICE_NAME)
    private String localEndpointServiceName;
    @Setter
    @Getter
    @Column(name = LOCAL_ENDPOINT_IPV4, storageOnly = true)
    private String localEndpointIPV4;
    @Setter
    @Getter
    @Column(name = LOCAL_ENDPOINT_IPV6, storageOnly = true)
    private String localEndpointIPV6;
    @Setter
    @Getter
    @Column(name = LOCAL_ENDPOINT_PORT, storageOnly = true)
    private int localEndpointPort;
    @Setter
    @Getter
    @Column(name = REMOTE_ENDPOINT_SERVICE_NAME)
    private String remoteEndpointServiceName;
    @Setter
    @Getter
    @Column(name = REMOTE_ENDPOINT_IPV4, storageOnly = true)
    private String remoteEndpointIPV4;
    @Setter
    @Getter
    @Column(name = REMOTE_ENDPOINT_IPV6, storageOnly = true)
    private String remoteEndpointIPV6;
    @Setter
    @Getter
    @Column(name = REMOTE_ENDPOINT_PORT, storageOnly = true)
    private int remoteEndpointPort;
    @Setter
    @Getter
    @Column(name = ANNOTATIONS, storageOnly = true, length = 50000)
    private JsonObject annotations;
    @Setter
    @Getter
    @Column(name = TAGS, storageOnly = true, length = 50000)
    private JsonObject tags;
    @Setter
    @Getter
    @Column(name = DEBUG)
    private int debug;
    @Setter
    @Getter
    @Column(name = SHARED)
    private int shared;
    @Setter
    @Getter
    @Column(name = QUERY, indexOnly = true, length = QUERY_LENGTH)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_QUERY_TABLE})
    private List<String> query;

    @Override
    public StorageID id() {
        return new StorageID().append(TRACE_ID, traceId).append(SPAN_ID, spanId);
    }

    @Override
    public SpanWrapper getSpanWrapper() {
        Span span = buildSpanFromRecord(this);
        SpanWrapper.Builder builder = SpanWrapper.newBuilder();
        builder.setSpan(ByteString.copyFrom(SpanBytesEncoder.PROTO3.encode(span)));
        builder.setSource(Source.ZIPKIN);
        return builder.build();
    }

    public static class Builder implements StorageBuilder<ZipkinSpanRecord> {
        @Override
        public ZipkinSpanRecord storage2Entity(final Convert2Entity converter) {
            ZipkinSpanRecord record = new ZipkinSpanRecord();
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setSpanId((String) converter.get(SPAN_ID));
            record.setParentId((String) converter.get(PARENT_ID));
            record.setName((String) converter.get(NAME));
            record.setKind((String) converter.get(KIND));
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            record.setTimestampMillis(((Number) converter.get(TIMESTAMP_MILLIS)).longValue());
            record.setDuration(((Number) converter.get(DURATION)).longValue());
            record.setLocalEndpointServiceName((String) converter.get(LOCAL_ENDPOINT_SERVICE_NAME));
            record.setLocalEndpointIPV4((String) converter.get(LOCAL_ENDPOINT_IPV4));
            record.setLocalEndpointIPV6((String) converter.get(LOCAL_ENDPOINT_IPV6));
            if (converter.get(LOCAL_ENDPOINT_PORT) != null) {
                record.setLocalEndpointPort(((Number) converter.get(LOCAL_ENDPOINT_PORT)).intValue());
            }
            record.setRemoteEndpointServiceName((String) converter.get(REMOTE_ENDPOINT_SERVICE_NAME));
            record.setRemoteEndpointIPV4((String) converter.get(REMOTE_ENDPOINT_IPV4));
            record.setRemoteEndpointIPV6((String) converter.get(REMOTE_ENDPOINT_IPV6));
            if (converter.get(REMOTE_ENDPOINT_PORT) != null) {
                record.setRemoteEndpointPort(((Number) converter.get(REMOTE_ENDPOINT_PORT)).intValue());
            }
            final String annotationsString = (String) converter.get(ANNOTATIONS);
            if (StringUtil.isNotEmpty(annotationsString)) {
                record.setAnnotations(GSON.fromJson(annotationsString, JsonObject.class));
            }
            final String tagsString = (String) converter.get(TAGS);
            if (StringUtil.isNotEmpty(tagsString)) {
                record.setTags(GSON.fromJson(tagsString, JsonObject.class));
            }
            if (converter.get(DEBUG) != null) {
                record.setDebug(((Number) converter.get(DEBUG)).intValue());
            }
            if (converter.get(SHARED) != null) {
                record.setShared(((Number) converter.get(SHARED)).intValue());
            }
            return record;
        }

        @Override
        public void entity2Storage(final ZipkinSpanRecord storageData, final Convert2Storage converter) {
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(SPAN_ID, storageData.getSpanId());
            converter.accept(PARENT_ID, storageData.getParentId());
            converter.accept(NAME, storageData.getName());
            converter.accept(KIND, storageData.getKind());
            converter.accept(TIMESTAMP, storageData.getTimestamp());
            converter.accept(TIMESTAMP_MILLIS, storageData.getTimestampMillis());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(DURATION, storageData.getDuration());
            converter.accept(LOCAL_ENDPOINT_SERVICE_NAME, storageData.getLocalEndpointServiceName());
            converter.accept(LOCAL_ENDPOINT_IPV4, storageData.getLocalEndpointIPV4());
            converter.accept(LOCAL_ENDPOINT_IPV6, storageData.getLocalEndpointIPV6());
            converter.accept(LOCAL_ENDPOINT_PORT, storageData.getLocalEndpointPort());
            converter.accept(REMOTE_ENDPOINT_SERVICE_NAME, storageData.getRemoteEndpointServiceName());
            converter.accept(REMOTE_ENDPOINT_IPV4, storageData.getRemoteEndpointIPV4());
            converter.accept(REMOTE_ENDPOINT_IPV6, storageData.getRemoteEndpointIPV6());
            converter.accept(REMOTE_ENDPOINT_PORT, storageData.getRemoteEndpointPort());
            if (storageData.getAnnotations() != null) {
                converter.accept(ANNOTATIONS, GSON.toJson(storageData.getAnnotations()));
            } else {
                converter.accept(ANNOTATIONS, Const.EMPTY_STRING);
            }
            if (storageData.getTags() != null) {
                converter.accept(TAGS, GSON.toJson(storageData.getTags()));
            } else {
                converter.accept(TAGS, Const.EMPTY_STRING);
            }
            converter.accept(QUERY, storageData.getQuery());
            converter.accept(DEBUG, storageData.getDebug());
            converter.accept(SHARED, storageData.getShared());
        }
    }

    public static Span buildSpanFromRecord(ZipkinSpanRecord record) {
        Span.Builder span = Span.newBuilder();
        span.traceId(record.getTraceId());
        span.id(record.getSpanId());
        span.parentId(record.getParentId());
        if (!StringUtil.isEmpty(record.getKind())) {
            span.kind(Span.Kind.valueOf(record.getKind()));
        }
        span.timestamp(record.getTimestamp());
        span.duration(record.getDuration());
        span.name(record.getName());
        //Build localEndpoint
        Endpoint.Builder localEndpoint = Endpoint.newBuilder();
        localEndpoint.serviceName(record.getLocalEndpointServiceName());
        if (!StringUtil.isEmpty(record.getLocalEndpointIPV4())) {
            localEndpoint.parseIp(record.getLocalEndpointIPV4());
        } else {
            localEndpoint.parseIp(record.getLocalEndpointIPV6());
        }
        localEndpoint.port(record.getLocalEndpointPort());
        span.localEndpoint(localEndpoint.build());
        //Build remoteEndpoint
        Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
        remoteEndpoint.serviceName(record.getRemoteEndpointServiceName());
        if (!StringUtil.isEmpty(record.getRemoteEndpointIPV4())) {
            remoteEndpoint.parseIp(record.getRemoteEndpointIPV4());
        } else {
            remoteEndpoint.parseIp(record.getRemoteEndpointIPV6());
        }
        remoteEndpoint.port(record.getRemoteEndpointPort());
        span.remoteEndpoint(remoteEndpoint.build());

        //Build tags
        JsonObject tagsJson = record.getTags();
        if (tagsJson != null) {
            for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
                span.putTag(tag.getKey(), tag.getValue().getAsString());
            }
        }
        //Build annotation
        JsonObject annotationJson = record.getAnnotations();
        if (annotationJson != null) {
            for (Map.Entry<String, JsonElement> annotation : annotationJson.entrySet()) {
                span.addAnnotation(Long.parseLong(annotation.getKey()), annotation.getValue().getAsString());
            }
        }
        span.debug(record.getDebug() != 0);
        span.shared(record.getShared() != 0);
        return span.build();
    }
}

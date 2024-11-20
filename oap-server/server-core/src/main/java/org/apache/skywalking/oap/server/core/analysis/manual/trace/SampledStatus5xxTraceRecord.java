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

package org.apache.skywalking.oap.server.core.analysis.manual.trace;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS_RELATION_CATALOG_NAME;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SAMPLED_STATUS_5XX_TRACE;

@Setter
@Getter
@ScopeDeclaration(id = SAMPLED_STATUS_5XX_TRACE, name = "SampledStatus5xxTraceRecord", catalog = PROCESS_RELATION_CATALOG_NAME)
@Stream(name = SampledStatus5xxTraceRecord.INDEX_NAME, scopeId = SAMPLED_STATUS_5XX_TRACE, builder = SampledStatus5xxTraceRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(SampledStatus5xxTraceRecord.TIMESTAMP)
public class SampledStatus5xxTraceRecord extends Record {

    public static final String INDEX_NAME = "sampled_status_5xx_trace_record";

    public static final String SCOPE = "scope";
    public static final String ENTITY_ID = "entity_id";
    public static final String TRACE_ID = TopN.TRACE_ID;
    public static final String URI = TopN.STATEMENT;
    public static final String LATENCY = "latency";
    public static final String TIMESTAMP = "timestamp";

    @Column(name = SCOPE)
    private int scope;
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;
    @Column(name = TRACE_ID, storageOnly = true)
    private String traceId;
    @Column(name = URI, storageOnly = true)
    private String uri;
    @Column(name = LATENCY, dataType = Column.ValueDataType.SAMPLED_RECORD)
    private long latency;
    @Setter
    @Getter
    @Column(name = TIMESTAMP)
    private long timestamp;

    @Override
    public StorageID id() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, entityId)
            .append(TRACE_ID, traceId);
    }

    public static class Builder implements StorageBuilder<SampledStatus5xxTraceRecord> {

        @Override
        public SampledStatus5xxTraceRecord storage2Entity(Convert2Entity converter) {
            final SampledStatus5xxTraceRecord record = new SampledStatus5xxTraceRecord();
            record.setScope(((Number) converter.get(SCOPE)).intValue());
            record.setEntityId((String) converter.get(ENTITY_ID));
            record.setTraceId((String) converter.get(TRACE_ID));
            record.setUri((String) converter.get(URI));
            record.setLatency(((Number) converter.get(LATENCY)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(SampledStatus5xxTraceRecord entity, Convert2Storage converter) {
            converter.accept(SCOPE, entity.getScope());
            converter.accept(ENTITY_ID, entity.getEntityId());
            converter.accept(TRACE_ID, entity.getTraceId());
            converter.accept(URI, entity.getUri());
            converter.accept(LATENCY, entity.getLatency());
            converter.accept(TIME_BUCKET, entity.getTimeBucket());
            converter.accept(TIMESTAMP, entity.getTimestamp());
        }
    }
}

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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BanyanDBRecordsQueryDAO extends AbstractBanyanDBDAO implements IRecordsQueryDAO {

    public BanyanDBRecordsQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Record> readRecords(RecordCondition condition, String valueColumnName, Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTrace.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB records");
                span.setMsg(
                    "Condition: " + condition + ", ValueColumnName: " + valueColumnName + ", Duration: " + duration);
            }
            return invokeReadRecords(condition, valueColumnName, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    public List<Record> invokeReadRecords(RecordCondition condition, String valueColumnName, Duration duration) throws IOException {
        final String modelName = condition.getName();
        final TimestampRange timestampRange = new TimestampRange(duration.getStartTimestamp(), duration.getEndTimestamp());
        final Set<String> tags = ImmutableSet.of(TopN.ENTITY_ID, TopN.STATEMENT, TopN.TRACE_ID, valueColumnName);
        StreamQueryResponse resp = traceRecordsTopN(modelName, tags,
                timestampRange, valueColumnName, condition.getTopN(), condition.getOrder(), new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        query.and(eq(TopN.ENTITY_ID, condition.getParentEntity().buildId()));
                        if (condition.getOrder() == Order.DES) {
                            query.setOrderBy(new StreamQuery.OrderBy(valueColumnName, AbstractQuery.Sort.DESC));
                        } else {
                            query.setOrderBy(new StreamQuery.OrderBy(valueColumnName, AbstractQuery.Sort.ASC));
                        }
                        query.setLimit(condition.getTopN());
                    }
                });

        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(modelName);
        if (schema == null) {
            throw new IOException("schema is not registered");
        }

        MetadataRegistry.ColumnSpec spec = schema.getSpec(valueColumnName);
        if (spec == null) {
            throw new IOException("field spec is not registered");
        }

        List<Record> results = new ArrayList<>(condition.getTopN());

        for (final RowEntity e : resp.getElements()) {
            Record record = new Record();
            final String refId = e.getTagValue(TopN.TRACE_ID);
            record.setName(e.getTagValue(TopN.STATEMENT));
            record.setRefId(StringUtil.isEmpty(refId) ? "" : refId);
            record.setId(record.getRefId());
            record.setValue(extractFieldValueAsString(spec, valueColumnName, e));
            results.add(record);
        }

        return results;
    }

    private String extractFieldValueAsString(MetadataRegistry.ColumnSpec spec, String fieldName, RowEntity e) throws IOException {
        if (double.class.equals(spec.getColumnClass())) {
            return String.valueOf(ByteUtil.bytes2Double(e.getTagValue(fieldName)).longValue());
        } else if (String.class.equals(spec.getColumnClass())) {
            return e.getTagValue(fieldName);
        } else {
            return String.valueOf(((Number) e.getTagValue(fieldName)).longValue());
        }
    }

    private StreamQueryResponse traceRecordsTopN(String modelName,
                                                 Set<String> tags,
                                                 TimestampRange timestampRange,
                                                 String valueColumnName,
                                                 int topN,
                                                 Order order,
                                                 QueryBuilder<StreamQuery> queryBuilder) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTrace.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB records TopN");
                builder.append("Condition: ")
                       .append(modelName)
                       .append(", Tags: ")
                       .append(tags)
                       .append(", TimestampRange: ")
                       .append(timestampRange)
                       .append(", ValueColumnName: ")
                       .append(valueColumnName)
                       .append(", TopN: ")
                       .append(topN)
                       .append(", Order: ")
                       .append(order);
                span.setMsg(builder.toString());
            }

            StreamQueryResponse response = query(modelName, tags, timestampRange, queryBuilder);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response));
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }
}

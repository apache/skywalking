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

import com.google.gson.Gson;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.And;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.Or;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.Span;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.banyandb.v1.client.TopNQuery;
import org.apache.skywalking.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.banyandb.v1.client.Trace;
import org.apache.skywalking.oap.server.core.query.input.AttrCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    private static final Instant UPPER_BOUND = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final TimestampRange LARGEST_TIME_RANGE = new TimestampRange(0, UPPER_BOUND.toEpochMilli());

    protected static final long UPPER_BOUND_TIME = UPPER_BOUND.toEpochMilli();

    protected static final long LOWER_BOUND_TIME = 0;

    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    protected StreamQueryResponse query(boolean isColdStage,
                                        String streamModelName,
                                        Set<String> tags,
                                        QueryBuilder<StreamQuery> builder) throws IOException {
        return this.query(isColdStage, streamModelName, tags, null, builder);
    }

    protected StreamQueryResponse query(boolean isColdStage,
                                        String streamModelName,
                                        Set<String> tags,
                                        TimestampRange timestampRange,
                                        QueryBuilder<StreamQuery> builder) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(streamModelName);
        if (schema == null) {
            throw new IllegalArgumentException("schema is not registered");
        }
        final StreamQuery query;
        if (timestampRange == null) {
            query = new StreamQuery(List.of(schema.getMetadata().getGroup()), schema.getMetadata().name(), LARGEST_TIME_RANGE, tags);
        } else {
            query = new StreamQuery(List.of(schema.getMetadata().getGroup()), schema.getMetadata().name(), timestampRange, tags);
        }
        if (isColdStage) {
            query.setStages(Set.of(BanyanDBStorageConfig.StageName.cold.name()));
        }

        builder.apply(query);
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        if (traceContext != null && traceContext.isDebug()) {
            query.enableTrace();
        }
        return getClient().query(query);
    }

    protected StreamQueryResponse queryDebuggable(boolean isColdStage,
                                                  String modelName,
                                                  Set<String> tags,
                                                  TimestampRange timestampRange,
                                                  QueryBuilder<StreamQuery> queryBuilder) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB Stream");
                MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(modelName);
                builder.append("Condition: ")
                       .append("modelName:")
                       .append(modelName)
                       .append(", Schema: ")
                       .append(schema)
                       .append(", Tags: ")
                       .append(tags)
                       .append(", TimestampRange: ")
                       .append(timestampRange)
                       .append(", Is cold data query: ")
                       .append(isColdStage);
                span.setMsg(builder.toString());
            }
            StreamQueryResponse response = query(isColdStage, modelName, tags, timestampRange, queryBuilder);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response.getElements()));
                span.setMsg(builder.toString());
            }
            addDBTrace2DebuggingTrace(response.getTrace(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    protected TopNQueryResponse topNQueryDebuggable(boolean isColdStage,
                                                    MetadataRegistry.Schema schema,
                                                    TimestampRange timestampRange,
                                                    int number,
                                                    AbstractQuery.Sort sort,
                                                    List<KeyValue> additionalConditions,
                                                    List<AttrCondition> attributes) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB TopNQuery");
                builder.append("Condition: ")
                       .append("Schema: ")
                       .append(schema)
                       .append(", TimestampRange: ")
                       .append(timestampRange)
                       .append(", Number: ")
                       .append(number)
                       .append(", Sort: ")
                       .append(sort)
                       .append(", AdditionalConditions: ")
                       .append(additionalConditions)
                       .append(", Attributes: ")
                       .append(attributes)
                       .append(", Is cold data query: ")
                       .append(isColdStage);
                span.setMsg(builder.toString());
            }
            TopNQueryResponse response = topNQuery(isColdStage, schema, timestampRange, number, sort, additionalConditions, attributes);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response.getTopNLists()));
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    private TopNQueryResponse topNQuery(boolean isColdStage,
                                        MetadataRegistry.Schema schema,
                                        TimestampRange timestampRange,
                                        int number,
                                        AbstractQuery.Sort sort,
                                        List<KeyValue> additionalConditions,
                                        List<AttrCondition> attributes) throws IOException {
        final TopNQuery q = new TopNQuery(List.of(schema.getMetadata().getGroup()), Objects.requireNonNull(
            schema.getTopNSpec()).getMetadata().getName(),
                                          timestampRange,
                                          number, sort);
        q.setAggregationType(MeasureQuery.Aggregation.Type.MEAN);
        List<PairQueryCondition<?>> conditions = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(additionalConditions)) {
            for (final KeyValue kv : additionalConditions) {
                conditions.add(PairQueryCondition.StringQueryCondition.eq(kv.getKey(), kv.getValue()));
            }
        }
        if (CollectionUtils.isNotEmpty(attributes)) {
            attributes.forEach(attr -> {
                if (attr.isEquals()) {
                    conditions.add(PairQueryCondition.StringQueryCondition.eq(attr.getKey(), attr.getValue()));
                } else {
                    conditions.add(PairQueryCondition.StringQueryCondition.ne(attr.getKey(), attr.getValue()));
                }
            });
        }
        q.setConditions(conditions);
        if (isColdStage) {
            q.setStages(List.of(BanyanDBStorageConfig.StageName.cold.name()));
        }

        return getClient().query(q);
    }

    protected MeasureQueryResponse queryDebuggable(boolean isColdStage,
                                                   MetadataRegistry.Schema schema,
                                                   Set<String> tags,
                                                   Set<String> fields,
                                                   TimestampRange timestampRange,
                                                   QueryBuilder<MeasureQuery> queryBuilder) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB Measure");
                builder.append("Condition: ")
                       .append("Schema: ")
                       .append(schema)
                       .append(", Tags: ")
                       .append(tags)
                       .append(", Fields: ")
                       .append(fields)
                       .append(", TimestampRange: ")
                       .append(timestampRange)
                       .append(", Is cold data query: ")
                       .append(isColdStage);
                span.setMsg(builder.toString());
            }
            MeasureQueryResponse response = query(isColdStage, schema, tags, fields, timestampRange, queryBuilder);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response.getDataPoints()));
                span.setMsg(builder.toString());
            }
            addDBTrace2DebuggingTrace(response.getTrace(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    protected MeasureQueryResponse query(boolean isColdStage,
                                         MetadataRegistry.Schema schema,
                                         Set<String> tags,
                                         Set<String> fields,
                                         QueryBuilder<MeasureQuery> builder) throws IOException {
        return query(isColdStage, schema, tags, fields, null, builder);
    }

    protected MeasureQueryResponse query(boolean isColdStage,
                                         MetadataRegistry.Schema schema,
                                         Set<String> tags,
                                         Set<String> fields,
                                         TimestampRange timestampRange,
                                         QueryBuilder<MeasureQuery> builder) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("measure is not registered");
        }
        final MeasureQuery query;
        if (timestampRange == null) {
            query = new MeasureQuery(List.of(schema.getMetadata().getGroup()), schema.getMetadata().name(), LARGEST_TIME_RANGE, tags, fields);
        } else {
            query = new MeasureQuery(List.of(schema.getMetadata().getGroup()), schema.getMetadata().name(), timestampRange, tags, fields);
        }
        if (isColdStage) {
            query.setStages(Set.of(BanyanDBStorageConfig.StageName.cold.name()));
        }

        builder.apply(query);
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        if (traceContext != null && traceContext.isDebug()) {
            query.enableTrace();
        }
        return getClient().query(query);
    }

    private void addDBTrace2DebuggingTrace(Trace trace, DebuggingTraceContext traceContext, DebuggingSpan parentSpan) {
        if (traceContext == null || parentSpan == null || trace == null) {
            return;
        }
        trace.getSpans().forEach(span -> addDBSpan2DebuggingTrace(span, traceContext, parentSpan));
    }

    private void addDBSpan2DebuggingTrace(Span span, DebuggingTraceContext traceContext, DebuggingSpan parentSpan) {
        DebuggingSpan debuggingSpan = traceContext.createSpanForTransform("BanyanDB: " + span.getMessage());
        debuggingSpan.setStartTime(span.getStartTime().getSeconds() * 1000_000_000 + span.getStartTime().getNanos());
        debuggingSpan.setEndTime(span.getEndTime().getSeconds() * 1000_000_000 + span.getEndTime().getNanos());
        debuggingSpan.setDuration(span.getDuration());
        debuggingSpan.setParentSpanId(parentSpan.getSpanId());
        debuggingSpan.setMsg(span.getTags().toString());
        if (span.isError()) {
            debuggingSpan.setError("BanyanDB occurs error.");
        }
        span.getChildren().forEach(child -> addDBSpan2DebuggingTrace(child, traceContext, debuggingSpan));
    }

    protected static QueryBuilder<MeasureQuery> emptyMeasureQuery() {
        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
            }
        };
    }

    protected abstract static class QueryBuilder<T extends AbstractQuery<? extends com.google.protobuf.GeneratedMessageV3>> {
        protected abstract void apply(final T query);

        protected PairQueryCondition<Long> eq(String name, long value) {
            return PairQueryCondition.LongQueryCondition.eq(name, value);
        }

        protected PairQueryCondition<List<String>> having(String name, List<String> value) {
            return PairQueryCondition.StringArrayQueryCondition.having(name, value);
        }

        protected PairQueryCondition<Long> lte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.le(name, value);
        }

        protected PairQueryCondition<Long> gte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ge(name, value);
        }

        protected PairQueryCondition<Long> gt(String name, long value) {
            return PairQueryCondition.LongQueryCondition.gt(name, value);
        }

        protected PairQueryCondition<String> eq(String name, String value) {
            return PairQueryCondition.StringQueryCondition.eq(name, value);
        }

        protected PairQueryCondition<String> ne(String name, String value) {
            return PairQueryCondition.StringQueryCondition.ne(name, value);
        }

        protected PairQueryCondition<String> match(String name, String value) {
            return PairQueryCondition.StringQueryCondition.match(name, value);
        }

        protected PairQueryCondition<String> match(String name, String value, BanyandbModel.Condition.MatchOption matchOption) {
            return PairQueryCondition.StringQueryCondition.match(name, value, matchOption);
        }

        protected PairQueryCondition<List<String>> in(String name, List<String> values) {
            return PairQueryCondition.StringArrayQueryCondition.in(name, values);
        }

        protected PairQueryCondition<List<String>> notIn(String name, List<String> values) {
            return PairQueryCondition.StringArrayQueryCondition.in(name, values);
        }

        protected PairQueryCondition<Long> ne(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ne(name, value);
        }

        protected AbstractCriteria and(List<? extends AbstractCriteria> conditions) {
            if (conditions.isEmpty()) {
                return null;
            }
            if (conditions.size() == 1) {
                return conditions.get(0);
            }

            return conditions.subList(2, conditions.size()).stream().reduce(
                    And.create(conditions.get(0), conditions.get(1)),
                    (BiFunction<AbstractCriteria, AbstractCriteria, AbstractCriteria>) And::create,
                    And::create);
        }

        protected AbstractCriteria or(List<? extends AbstractCriteria> conditions) {
            if (conditions.isEmpty()) {
                return null;
            }
            if (conditions.size() == 1) {
                return conditions.get(0);
            }
            return conditions.subList(2, conditions.size()).stream().reduce(
                    Or.create(conditions.get(0), conditions.get(1)),
                    (BiFunction<AbstractCriteria, AbstractCriteria, AbstractCriteria>) Or::create,
                    Or::create);
        }
    }

    protected TimestampRange getTimestampRange(@Nullable Duration duration) {
        long startTimeMillis = 0;
        long endTimeMillis = 0;
        if (duration != null) {
            startTimeMillis = duration.getStartTimestamp();
            endTimeMillis = duration.getEndTimestamp();
        }
        TimestampRange tsRange = null;

        if (startTimeMillis > 0 && endTimeMillis > 0) {
            tsRange = new TimestampRange(startTimeMillis, endTimeMillis);
        }

        return tsRange;
    }
}

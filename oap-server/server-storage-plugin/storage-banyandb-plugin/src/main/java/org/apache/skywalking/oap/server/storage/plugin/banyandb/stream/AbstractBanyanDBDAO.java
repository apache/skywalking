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
import java.util.Collections;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.Span;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.library.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.Trace;
import org.apache.skywalking.library.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.Value;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;
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

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    private static final Instant UPPER_BOUND = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final TimestampRange LARGEST_TIME_RANGE = new TimestampRange(0, UPPER_BOUND.toEpochMilli());

    protected static final long UPPER_BOUND_TIME = UPPER_BOUND.toEpochMilli();

    protected static final long LOWER_BOUND_TIME = 0;

    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    /**
     * Query a stream via BydbQL. Projects the given tags (or {@code *} when empty) and appends the
     * WHERE / ORDER BY / LIMIT tail carried by {@code where}.
     *
     * @param isColdStage     whether to target the cold lifecycle stage
     * @param streamModelName the record/stream model name to resolve the schema
     * @param tags            tag columns to project
     * @param timestampRange  the time range bound to {@code TIME BETWEEN ? AND ?}
     * @param where           the fluent condition builder holding the clause tail and its params
     * @return the stream query response
     * @throws IOException if the query fails
     */
    protected StreamQueryResponse queryDebuggable(boolean isColdStage,
                                                  String streamModelName,
                                                  Set<String> tags,
                                                  TimestampRange timestampRange,
                                                  Conditions where) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(streamModelName);
        if (schema == null) {
            throw new IllegalArgumentException("schema is not registered");
        }
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            final boolean debug = traceContext != null && traceContext.isDebug();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB Stream");
            }
            final StringBuilder ql = new StringBuilder("SELECT ").append(projection(tags, Collections.emptySet()))
                .append(" FROM STREAM ").append(schema.getMetadata().name())
                .append(" IN ").append(schema.getMetadata().getGroup());
            if (isColdStage) {
                ql.append(" ON ").append(BanyanDBStorageConfig.StageName.cold.name()).append(" STAGES");
            }
            ql.append(" TIME BETWEEN ? AND ?");
            appendBodyWithTrace(ql, where.buildQl(), debug);
            final List<Serializable<BanyandbModel.TagValue>> params = timeBoundedParams(timestampRange, where.params());
            final StreamQueryResponse response =
                getClient().queryStream(ql.toString(), params.toArray(new Serializable[0]));
            if (span != null) {
                final StringBuilder msg = new StringBuilder("BydbQL: ").append(ql)
                    .append("\n Params: ").append(bindings(params));
                if (traceContext.isDumpStorageRsp()) {
                    msg.append("\n Response: ").append(new Gson().toJson(response.getElements()));
                }
                span.setMsg(msg.toString());
            }
            addDBTrace2DebuggingTrace(response.getTrace(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * Server-side TopN over a pre-aggregated TopN rule via BydbQL {@code SHOW TOP} (MEAN aggregation).
     * Only equality conditions/attributes are applied — not-equals is handled by a separate exclude rule,
     * as in the typed path.
     *
     * @param isColdStage          whether to target the cold lifecycle stage
     * @param schema               the source measure schema (its group hosts the TopN rule)
     * @param timestampRange       the time range bound to {@code TIME BETWEEN ? AND ?}
     * @param number               the TopN count bound to {@code SHOW TOP ?}
     * @param sort                 the ranking direction
     * @param additionalConditions equality conditions to AND into the WHERE clause
     * @param attributes           attribute conditions; only equality ones are applied
     * @param topNRuleName         the pre-aggregated TopN measure name in the FROM clause
     * @return the TopN query response
     * @throws IOException if the query fails
     */
    protected TopNQueryResponse topNQueryDebuggable(boolean isColdStage,
                                                    MetadataRegistry.Schema schema,
                                                    TimestampRange timestampRange,
                                                    int number,
                                                    AbstractQuery.Sort sort,
                                                    List<KeyValue> additionalConditions,
                                                    List<AttrCondition> attributes,
                                                    String topNRuleName) throws IOException {
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            final boolean debug = traceContext != null && traceContext.isDebug();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB TopN");
            }
            final Conditions where = Conditions.create();
            if (CollectionUtils.isNotEmpty(additionalConditions)) {
                for (final KeyValue kv : additionalConditions) {
                    where.eq(qualify(kv.getKey()), kv.getValue());
                }
            }
            if (CollectionUtils.isNotEmpty(attributes)) {
                for (final AttrCondition attr : attributes) {
                    // server-side TopN does not support not-equals; those are handled by a dedicated
                    // exclude rule, so only equality attributes are applied here (matches the typed path).
                    if (attr.isEquals()) {
                        where.eq(qualify(attr.getKey()), attr.getValue());
                    }
                }
            }
            final List<Serializable<BanyandbModel.TagValue>> params = new ArrayList<>();
            params.add(Value.longTagValue((long) number));
            params.add(Value.timestampTagValue(timestampRange.getBegin()));
            params.add(Value.timestampTagValue(timestampRange.getEnd()));
            params.addAll(where.params());
            final StringBuilder ql = new StringBuilder("SHOW TOP ? FROM MEASURE ")
                .append(topNRuleName).append(" IN ").append(schema.getMetadata().getGroup());
            if (isColdStage) {
                ql.append(" ON ").append(BanyanDBStorageConfig.StageName.cold.name()).append(" STAGES");
            }
            ql.append(" TIME BETWEEN ? AND ?").append(where.buildQl())
              .append(" AGGREGATE BY MEAN ORDER BY ").append(sort == AbstractQuery.Sort.DESC ? "DESC" : "ASC");
            if (debug) {
                ql.append(" WITH QUERY_TRACE");
            }
            final TopNQueryResponse response =
                getClient().queryTopN(ql.toString(), params.toArray(new Serializable[0]));
            if (span != null) {
                final StringBuilder msg = new StringBuilder("BydbQL: ").append(ql)
                    .append("\n Params: ").append(bindings(params));
                if (traceContext.isDumpStorageRsp()) {
                    msg.append("\n Response: ").append(new Gson().toJson(response.getTopNLists()));
                }
                span.setMsg(msg.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * Ad-hoc TopN over a raw measure via BydbQL {@code SELECT TOP} (no pre-aggregated rule), returning
     * measure DataPoints. Unlike the general measure {@code queryDebuggable}, this supports not-equals
     * conditions in {@code where}.
     *
     * @param isColdStage    whether to target the cold lifecycle stage
     * @param schema         the source measure schema
     * @param timestampRange the time range bound to {@code TIME BETWEEN ? AND ?}; null means all time
     * @param valueColumn       the field to rank by and aggregate
     * @param aggregateFunction the BydbQL aggregate function applied to {@code valueColumn}, chosen by the
     *                          caller (e.g. {@code MEAN})
     * @param groupByColumn     the tag to group by (e.g. entity_id)
     * @param number            the TopN count bound to {@code SELECT TOP ?}
     * @param sort              the ranking direction
     * @param where             the WHERE conditions (equality and not-equality)
     * @return the measure query response (DataPoints)
     * @throws IOException if the query fails
     */
    protected MeasureQueryResponse queryDebuggable(boolean isColdStage,
                                                   MetadataRegistry.Schema schema,
                                                   TimestampRange timestampRange,
                                                   String valueColumn,
                                                   String aggregateFunction,
                                                   String groupByColumn,
                                                   int number,
                                                   AbstractQuery.Sort sort,
                                                   Conditions where) throws IOException {
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            final boolean debug = traceContext != null && traceContext.isDebug();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB TopN Measure");
            }
            final String value = qualify(valueColumn);
            // GROUP BY <value> needs <value> in the field projection, but the transformer projects only plain
            // columns (not the TOP order field or the aggregate) — so project value plainly in addition to the aggregate.
            final StringBuilder ql = new StringBuilder("SELECT TOP ? ")
                .append(value).append(sort == AbstractQuery.Sort.DESC ? " DESC" : " ASC")
                .append(", ").append(aggregateFunction).append("(").append(value).append("), ")
                .append(value).append(", ").append(groupByColumn)
                .append(" FROM MEASURE ").append(schema.getMetadata().name())
                .append(" IN ").append(schema.getMetadata().getGroup());
            if (isColdStage) {
                ql.append(" ON ").append(BanyanDBStorageConfig.StageName.cold.name()).append(" STAGES");
            }
            ql.append(" TIME BETWEEN ? AND ?").append(where.buildQl())
              .append(" GROUP BY ").append(groupByColumn).append(", ").append(value);
            if (debug) {
                ql.append(" WITH QUERY_TRACE");
            }
            final List<Serializable<BanyandbModel.TagValue>> params = new ArrayList<>();
            params.add(Value.longTagValue((long) number));
            params.addAll(timeBoundedParams(timestampRange, where.params()));
            final MeasureQueryResponse response =
                getClient().queryMeasure(ql.toString(), params.toArray(new Serializable[0]));
            if (span != null) {
                final StringBuilder msg = new StringBuilder("BydbQL: ").append(ql)
                    .append("\n Params: ").append(bindings(params));
                if (traceContext.isDumpStorageRsp()) {
                    msg.append("\n Response: ").append(new Gson().toJson(response.getDataPoints()));
                }
                span.setMsg(msg.toString());
            }
            addDBTrace2DebuggingTrace(response.getTrace(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * Query a measure via BydbQL. Projects the given tags/fields and appends the WHERE / GROUP BY /
     * ORDER BY / LIMIT tail carried by {@code where}.
     *
     * @param isColdStage    whether to target the cold lifecycle stage
     * @param schema         the resolved measure schema (group + name)
     * @param tags           tag columns to project
     * @param fields         field columns to project
     * @param timestampRange the time range bound to {@code TIME BETWEEN ? AND ?}
     * @param where          the fluent condition builder holding the clause tail and its params
     * @return the measure query response
     * @throws IOException if the query fails
     */
    protected MeasureQueryResponse queryDebuggable(boolean isColdStage,
                                                   MetadataRegistry.Schema schema,
                                                   Set<String> tags,
                                                   Set<String> fields,
                                                   TimestampRange timestampRange,
                                                   Conditions where) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("measure is not registered");
        }
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            final boolean debug = traceContext != null && traceContext.isDebug();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB Measure");
            }
            final StringBuilder ql = new StringBuilder("SELECT ").append(projection(tags, fields))
                .append(" FROM MEASURE ").append(schema.getMetadata().name())
                .append(" IN ").append(schema.getMetadata().getGroup());
            if (isColdStage) {
                ql.append(" ON ").append(BanyanDBStorageConfig.StageName.cold.name()).append(" STAGES");
            }
            ql.append(" TIME BETWEEN ? AND ?");
            appendBodyWithTrace(ql, where.buildQl(), debug);
            final List<Serializable<BanyandbModel.TagValue>> params = timeBoundedParams(timestampRange, where.params());
            final MeasureQueryResponse response =
                getClient().queryMeasure(ql.toString(), params.toArray(new Serializable[0]));
            if (span != null) {
                final StringBuilder msg = new StringBuilder("BydbQL: ").append(ql)
                    .append("\n Params: ").append(bindings(params));
                if (traceContext.isDumpStorageRsp()) {
                    msg.append("\n Response: ").append(new Gson().toJson(response.getDataPoints()));
                }
                span.setMsg(msg.toString());
            }
            addDBTrace2DebuggingTrace(response.getTrace(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    private static String projection(Set<String> tags, Set<String> fields) {
        final List<String> cols = new ArrayList<>(tags.size() + fields.size());
        cols.addAll(tags);
        cols.addAll(fields);
        if (cols.isEmpty()) {
            return "*";
        }
        Collections.sort(cols);
        return cols.stream().map(AbstractBanyanDBDAO::qualify).collect(Collectors.joining(", "));
    }

    /**
     * Build the bound-parameter list for a BydbQL SELECT: the two {@code TIME BETWEEN ? AND ?} bounds first
     * (in that order, matching the two leading placeholders), then the caller's condition params.
     *
     * @param timestampRange the time range whose begin/end bind the two leading placeholders
     * @param originParams   params for the placeholders in the caller's condition clause, or null
     * @return the ordered param list ready to pass to the query stub
     */
    private static List<Serializable<BanyandbModel.TagValue>> timeBoundedParams(
        TimestampRange timestampRange, List<Serializable<BanyandbModel.TagValue>> originParams) {
        // A null range means "all time": callers querying the full history pass null instead of
        // constructing an explicit full-bound TimestampRange (which equals LARGEST_TIME_RANGE).
        final TimestampRange range = timestampRange == null ? LARGEST_TIME_RANGE : timestampRange;
        final List<Serializable<BanyandbModel.TagValue>> params =
            new ArrayList<>(2 + (originParams == null ? 0 : originParams.size()));
        params.add(Value.timestampTagValue(range.getBegin()));
        params.add(Value.timestampTagValue(range.getEnd()));
        if (originParams != null) {
            params.addAll(originParams);
        }
        return params;
    }

    /**
     * Append the clause tail, inserting {@code WITH QUERY_TRACE} (when debugging) before any
     * {@code LIMIT}/{@code OFFSET} — BydbQL requires the trace marker ahead of LIMIT/OFFSET or the parser
     * rejects it with {@code unexpected token "WITH"}.
     */
    private static void appendBodyWithTrace(StringBuilder ql, String body, boolean debug) {
        final String tail = body == null ? "" : body;
        if (!debug) {
            ql.append(tail);
            return;
        }
        final int limitIdx = tail.indexOf(" LIMIT ");
        if (limitIdx >= 0) {
            ql.append(tail, 0, limitIdx).append(" WITH QUERY_TRACE").append(tail, limitIdx, tail.length());
        } else {
            ql.append(tail).append(" WITH QUERY_TRACE");
        }
    }

    /**
     * Double-quote a column identifier for BydbQL. Quoting unconditionally sidesteps the reserved-keyword
     * check (e.g. a column named {@code count}) without tracking BanyanDB's keyword list.
     *
     * @param column the column name to emit in a BydbQL clause
     * @return the column wrapped in double quotes
     */
    protected static String qualify(String column) {
        return "\"" + column + "\"";
    }

    /**
     * Render the bound {@code ?} values for a debug-span message. Only called when a span exists, so
     * {@code serialize()} stays off the hot path.
     *
     * @param params the ordered bound parameters
     * @return a compact single-line list of the serialized parameter values
     */
    private static String bindings(List<Serializable<BanyandbModel.TagValue>> params) {
        return params.stream()
                     .map(p -> p.serialize().toString().replaceAll("\\s+", " ").trim())
                     .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Query a trace via BydbQL. Projects only raw span data ({@code SELECT ()}); tags remain usable in
     * the {@code where} tail (WHERE / ORDER BY).
     *
     * @param isColdStage    whether to target the cold lifecycle stage
     * @param traceModelName the trace model name to resolve the schema
     * @param timestampRange the time range bound to {@code TIME BETWEEN ? AND ?}
     * @param where          the fluent condition builder holding the clause tail and its params
     * @return the trace query response
     * @throws IOException if the query fails
     */
    protected TraceQueryResponse queryTraceDebuggable(boolean isColdStage,
                                                      String traceModelName,
                                                      TimestampRange timestampRange,
                                                      Conditions where) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(traceModelName);
        if (schema == null) {
            throw new IllegalArgumentException("schema is not registered");
        }
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            final boolean debug = traceContext != null && traceContext.isDebug();
            if (traceContext != null) {
                span = traceContext.createSpan("Query BanyanDB Trace");
            }
            final StringBuilder ql = new StringBuilder("SELECT () FROM TRACE ")
                .append(schema.getMetadata().name())
                .append(" IN ").append(schema.getMetadata().getGroup());
            if (isColdStage) {
                ql.append(" ON ").append(BanyanDBStorageConfig.StageName.cold.name()).append(" STAGES");
            }
            ql.append(" TIME BETWEEN ? AND ?");
            appendBodyWithTrace(ql, where.buildQl(), debug);
            final List<Serializable<BanyandbModel.TagValue>> params = timeBoundedParams(timestampRange, where.params());
            final TraceQueryResponse response =
                getClient().queryTrace(ql.toString(), params.toArray(new Serializable[0]));
            if (span != null) {
                final StringBuilder msg = new StringBuilder("BydbQL: ").append(ql)
                    .append("\n Params: ").append(bindings(params));
                if (traceContext.isDumpStorageRsp()) {
                    msg.append("\n Response: ").append(new Gson().toJson(response.getTraces()));
                }
                span.setMsg(msg.toString());
            }
            addDBTrace2DebuggingTrace(response.getTraceResult(), traceContext, span);
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
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
        } else {
            // default to last 24 hours
            Instant now = Instant.now();
            tsRange = new  TimestampRange(now.minusMillis(86400000).toEpochMilli(), now.toEpochMilli());
        }

        return tsRange;
    }
}

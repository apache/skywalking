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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.TraceTagAutocompleteData;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereNested;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.influxdb.querybuilder.clauses.Clause;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class TraceQuery implements ITraceQueryDAO {
    private final InfluxClient client;

    public TraceQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB,
                                       long endSecondTB,
                                       long minDuration,
                                       long maxDuration,
                                       String serviceId,
                                       String serviceInstanceId,
                                       String endpointId,
                                       String traceId,
                                       int limit,
                                       int from,
                                       TraceState traceState,
                                       QueryOrder queryOrder,
                                       final List<Tag> tags)
        throws IOException {

        String orderBy = SegmentRecord.START_TIME;
        if (queryOrder == QueryOrder.BY_DURATION) {
            orderBy = SegmentRecord.LATENCY;
        }

        WhereQueryImpl<SelectQueryImpl> recallQuery = select()
            .function(InfluxConstants.SORT_DES, orderBy, limit + from)
            .column(SegmentRecord.SEGMENT_ID)
            .column(SegmentRecord.START_TIME)
            .column(SegmentRecord.ENDPOINT_ID)
            .column(SegmentRecord.LATENCY)
            .column(SegmentRecord.IS_ERROR)
            .column(SegmentRecord.TRACE_ID)
            .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
            .where();

        if (startSecondTB != 0 && endSecondTB != 0) {
            recallQuery.and(gte(SegmentRecord.TIME_BUCKET, startSecondTB))
                       .and(lte(SegmentRecord.TIME_BUCKET, endSecondTB));
        }
        if (minDuration != 0) {
            recallQuery.and(gte(SegmentRecord.LATENCY, minDuration));
        }
        if (maxDuration != 0) {
            recallQuery.and(lte(SegmentRecord.LATENCY, maxDuration));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            recallQuery.and(eq(InfluxConstants.TagName.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            recallQuery.and(eq(SegmentRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (!com.google.common.base.Strings.isNullOrEmpty(endpointId)) {
            recallQuery.and(eq(SegmentRecord.ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            recallQuery.and(eq(SegmentRecord.TRACE_ID, traceId));
        }
        switch (traceState) {
            case ERROR:
                recallQuery.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.TRUE));
                break;
            case SUCCESS:
                recallQuery.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.FALSE));
                break;
            default:
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            WhereNested<WhereQueryImpl<SelectQueryImpl>> nested = recallQuery.andNested();
            for (final Tag tag : tags) {
                nested.and(contains(tag.getKey(), "'" + tag.getValue() + "'"));
            }
            nested.close();
        }

        WhereQueryImpl<SelectQueryImpl> countQuery = select()
            .count(SegmentRecord.ENDPOINT_ID)
            .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
            .where();
        for (Clause clause : recallQuery.getClauses()) {
            countQuery.where(clause);
        }
        Query query = new Query(countQuery.getCommand() + recallQuery.getCommand());

        List<QueryResult.Result> results = client.query(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), results);
        }
        if (results.size() != 2) {
            throw new IOException("Expecting to get 2 Results, but it is " + results.size());
        }
        List<QueryResult.Series> counter = results.get(0).getSeries();
        List<QueryResult.Series> result = results.get(1).getSeries();
        if (result == null || result.isEmpty()) {
            return new TraceBrief();
        }

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal(((Number) counter.get(0).getValues().get(0).get(1)).intValue());

        result.get(0).getValues().stream().sorted((a, b) -> {
            // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
            return Long.compare(((Number) b.get(1)).longValue(), ((Number) a.get(1)).longValue());
        }).skip(from).forEach(values -> {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId((String) values.get(2));
            basicTrace.setStart(String.valueOf(((Number) values.get(3)).longValue()));
            basicTrace.getEndpointNames()
                      .add(IDManager.EndpointID.analysisId((String) values.get(4)).getEndpointName());
            basicTrace.setDuration(((Number) values.get(5)).intValue());
            basicTrace.setError(BooleanUtils.valueToBoolean(((Number) values.get(6)).intValue()));
            basicTrace.getTraceIds().add((String) values.get(7));

            traceBrief.getTraces().add(basicTrace);
        });
        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        WhereQueryImpl<SelectQueryImpl> whereQuery = select().column(SegmentRecord.SEGMENT_ID)
                                                             .column(SegmentRecord.TRACE_ID)
                                                             .column(SegmentRecord.SERVICE_ID)
                                                             .column(SegmentRecord.SERVICE_INSTANCE_ID)
                                                             .column(SegmentRecord.START_TIME)
                                                             .column(SegmentRecord.LATENCY)
                                                             .column(SegmentRecord.IS_ERROR)
                                                             .column(SegmentRecord.DATA_BINARY)
                                                             .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
                                                             .where();

        whereQuery.and(eq(SegmentRecord.TRACE_ID, traceId));
        List<QueryResult.Series> series = client.queryForSeries(whereQuery);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", whereQuery.getCommand(), series);
        }
        if (series == null || series.isEmpty()) {
            return Collections.emptyList();
        }
        List<SegmentRecord> segmentRecords = Lists.newArrayList();
        series.get(0).getValues().forEach(values -> {
            SegmentRecord segmentRecord = new SegmentRecord();

            segmentRecord.setSegmentId((String) values.get(1));
            segmentRecord.setTraceId((String) values.get(2));
            segmentRecord.setServiceId((String) values.get(3));
            segmentRecord.setServiceInstanceId((String) values.get(4));
            segmentRecord.setStartTime(((Number) values.get(5)).longValue());
            segmentRecord.setLatency(((Number) values.get(6)).intValue());
            segmentRecord.setIsError(((Number) values.get(7)).intValue());

            String base64 = (String) values.get(8);
            if (!Strings.isNullOrEmpty(base64)) {
                segmentRecord.setDataBinary(Base64.getDecoder().decode(base64));
            }

            segmentRecords.add(segmentRecord);
        });

        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) {
        return Collections.emptyList();
    }

    @Override
    public Set<String> queryTraceTagAutocompleteKeys(final long startSecondTB,
                                                     final long endSecondTB) throws IOException {

        WhereQueryImpl<SelectQueryImpl> query = select()
            .function("distinct", TraceTagAutocompleteData.TAG_KEY)
            .from(client.getDatabase(), TraceTagAutocompleteData.INDEX_NAME)
            .where();
        appendTagAutocompleteCondition(startSecondTB, endSecondTB, query);

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptySet();
        }
        Set<String> tagKeys = new HashSet<>();
        for (List<Object> values : series.getValues()) {
            String tagKey = (String) values.get(1);
            tagKeys.add(tagKey);
        }

        return tagKeys;
    }

    @Override
    public Set<String> queryTraceTagAutocompleteValues(final String tagKey,
                                                       final int limit,
                                                       final long startSecondTB,
                                                       final long endSecondTB) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .column(TraceTagAutocompleteData.TAG_VALUE)
            .from(client.getDatabase(), TraceTagAutocompleteData.INDEX_NAME)
            .where();
        query.limit(limit);
        query.and(eq(TraceTagAutocompleteData.TAG_KEY, tagKey));
        appendTagAutocompleteCondition(startSecondTB, endSecondTB, query);
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptySet();
        }
        Set<String> tagValues = new HashSet<>();
        for (List<Object> values : series.getValues()) {
            String tagValue = (String) values.get(1);
            tagValues.add(tagValue);
        }

        return tagValues;
    }

    private void appendTagAutocompleteCondition(final long startSecondTB,
                                                final long endSecondTB,
                                                final WhereQueryImpl<SelectQueryImpl> query) {
        long startMinTB = startSecondTB / 100;
        long endMinTB = endSecondTB / 100;
        if (startMinTB > 0) {
            query.and(gte(TraceTagAutocompleteData.TIME_BUCKET, startMinTB));
        }
        if (endMinTB > 0) {
            query.and(lte(TraceTagAutocompleteData.TIME_BUCKET, endMinTB));
        }
    }
}

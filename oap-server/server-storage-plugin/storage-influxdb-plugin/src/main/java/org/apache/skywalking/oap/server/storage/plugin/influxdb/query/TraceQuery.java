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

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
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
import org.elasticsearch.common.Strings;
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
                                       String endpointName,
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
            .column(SegmentRecord.ENDPOINT_NAME)
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
        if (!Strings.isNullOrEmpty(endpointName)) {
            recallQuery.and(contains(SegmentRecord.ENDPOINT_NAME, endpointName.replaceAll("/", "\\\\/")));
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
            basicTrace.getEndpointNames().add((String) values.get(4));
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
                                                             .column(SegmentRecord.ENDPOINT_NAME)
                                                             .column(SegmentRecord.START_TIME)
                                                             .column(SegmentRecord.END_TIME)
                                                             .column(SegmentRecord.LATENCY)
                                                             .column(SegmentRecord.IS_ERROR)
                                                             .column(SegmentRecord.DATA_BINARY)
                                                             .column(SegmentRecord.VERSION)
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
            segmentRecord.setEndpointName((String) values.get(5));
            segmentRecord.setStartTime(((Number) values.get(6)).longValue());
            segmentRecord.setEndTime(((Number) values.get(7)).longValue());
            segmentRecord.setLatency(((Number) values.get(8)).intValue());
            segmentRecord.setIsError(((Number) values.get(9)).intValue());
            segmentRecord.setVersion(((Number) values.get(11)).intValue());

            String base64 = (String) values.get(10);
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
}

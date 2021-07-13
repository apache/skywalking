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

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.elasticsearch.common.Strings;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereNested;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.influxdb.querybuilder.clauses.ConjunctionClause;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.apm.util.StringUtil.isNotEmpty;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SPAN_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_SEGMENT_ID;
import static org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord.TIMESTAMP;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ALL_FIELDS;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class LogQuery implements ILogQueryDAO {
    private final InfluxClient client;

    public LogQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public Logs queryLogs(final String serviceId,
                          final String serviceInstanceId,
                          final String endpointId,
                          final String endpointName,
                          final TraceScopeCondition relatedTrace,
                          final Order queryOrder,
                          final int from,
                          final int limit,
                          final long startTB,
                          final long endTB,
                          final List<Tag> tags,
                          final List<String> keywordsOfContent,
                          final List<String> excludingKeywordsOfContent) throws IOException {
        WhereQueryImpl<SelectQueryImpl> recallQuery = select().raw(ALL_FIELDS)
                                                              .function(
                                                                  Order.DES.equals(
                                                                      queryOrder) ? InfluxConstants.SORT_DES : InfluxConstants.SORT_ASC,
                                                                  AbstractLogRecord.TIMESTAMP, limit + from
                                                              )
                                                              .from(client.getDatabase(), LogRecord.INDEX_NAME)
                                                              .where();

        if (isNotEmpty(serviceId)) {
            recallQuery.and(eq(InfluxConstants.TagName.SERVICE_ID, serviceId));
        }
        if (isNotEmpty(serviceInstanceId)) {
            recallQuery.and(eq(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (isNotEmpty(endpointId)) {
            recallQuery.and(eq(ENDPOINT_ID, endpointId));
        }
        if (isNotEmpty(endpointName)) {
            recallQuery.and(contains(ENDPOINT_NAME, endpointName.replaceAll("/", "\\\\/")));
        }
        if (nonNull(relatedTrace)) {
            if (isNotEmpty(relatedTrace.getTraceId())) {
                recallQuery.and(eq(TRACE_ID, relatedTrace.getTraceId()));
            }
            if (isNotEmpty(relatedTrace.getSegmentId())) {
                recallQuery.and(eq(TRACE_SEGMENT_ID, relatedTrace.getSegmentId()));
            }
            if (nonNull(relatedTrace.getSpanId())) {
                recallQuery.and(eq(SPAN_ID, relatedTrace.getSpanId()));
            }
        }
        if (startTB != 0 && endTB != 0) {
            recallQuery.and(gte(AbstractLogRecord.TIME_BUCKET, startTB))
                       .and(lte(AbstractLogRecord.TIME_BUCKET, endTB));
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            WhereNested<WhereQueryImpl<SelectQueryImpl>> nested = recallQuery.andNested();
            for (final Tag tag : tags) {
                nested.and(contains(tag.getKey(), "'" + tag.getValue() + "'"));
            }
            nested.close();
        }

        SelectQueryImpl countQuery = select().count(SERVICE_ID).from(client.getDatabase(), LogRecord.INDEX_NAME);
        for (ConjunctionClause clause : recallQuery.getClauses()) {
            countQuery.where(clause);
        }

        Query query = new Query(countQuery.getCommand() + recallQuery.getCommand());
        List<QueryResult.Result> results = client.query(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} \nresult set: {}", query.getCommand(), results);
        }
        if (results.size() != 2) {
            throw new IOException("Expecting to get 2 Results, but it is " + results.size());
        }

        final Logs logs = new Logs();
        QueryResult.Result counter = results.get(0);
        QueryResult.Result seriesList = results.get(1);

        logs.setTotal(((Number) counter.getSeries().get(0).getValues().get(0).get(1)).intValue());
        seriesList.getSeries().forEach(series -> {
            final List<String> columns = series.getColumns();

            series.getValues().forEach(values -> {
                Map<String, Object> data = Maps.newHashMap();
                Log log = new Log();

                for (int i = 1; i < columns.size(); i++) {
                    Object value = values.get(i);
                    if (value instanceof StorageDataComplexObject) {
                        value = ((StorageDataComplexObject) value).toStorageData();
                    }
                    data.put(columns.get(i), value);
                }
                log.setServiceId((String) data.get(SERVICE_ID));
                log.setServiceInstanceId((String) data.get(SERVICE_INSTANCE_ID));
                log.setEndpointId((String) data.get(ENDPOINT_ID));
                log.setEndpointName((String) data.get(ENDPOINT_NAME));
                log.setTraceId((String) data.get(TRACE_ID));
                log.setTimestamp(((Number) data.get(TIMESTAMP)).longValue());
                log.setContentType(
                    ContentType.instanceOf(((Number) data.get(AbstractLogRecord.CONTENT_TYPE)).intValue()));
                log.setContent((String) data.get(AbstractLogRecord.CONTENT));
                String dataBinaryBase64 = (String) data.get(AbstractLogRecord.TAGS_RAW_DATA);
                if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                    parserDataBinary(dataBinaryBase64, log.getTags());
                }
                logs.getLogs().add(log);
            });
        });

        return logs;
    }
}

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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.LogState;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.elasticsearch.common.Strings;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.influxdb.querybuilder.clauses.ConjunctionClause;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT_TYPE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.IS_ERROR;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.STATUS_CODE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TIMESTAMP;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_ID;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ALL_FIELDS;
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
    public Logs queryLogs(String metricName, int serviceId, int serviceInstanceId, String endpointId, String traceId,
                          LogState state, String stateCode, Pagination paging, int from, int limit,
                          long startTB, long endTB) throws IOException {
        WhereQueryImpl<SelectQueryImpl> recallQuery = select().raw(ALL_FIELDS)
                                                              .from(client.getDatabase(), metricName)
                                                              .where();
        if (serviceId != Const.NONE) {
            recallQuery.and(eq(InfluxConstants.TagName.SERVICE_ID, String.valueOf(serviceId)));
        }
        if (serviceInstanceId != Const.NONE) {
            recallQuery.and(eq(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (StringUtil.isNotEmpty(endpointId)) {
            recallQuery.and(eq(ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            recallQuery.and(eq(TRACE_ID, traceId));
        }
        switch (state) {
            case ERROR: {
                recallQuery.and(eq(IS_ERROR, true));
                break;
            }
            case SUCCESS: {
                recallQuery.and(eq(IS_ERROR, false));
                break;
            }
        }
        if (!Strings.isNullOrEmpty(stateCode)) {
            recallQuery.and(eq(STATUS_CODE, stateCode));
        }
        recallQuery.and(gte(AbstractLogRecord.TIME_BUCKET, startTB))
                   .and(lte(AbstractLogRecord.TIME_BUCKET, endTB));

        if (from > Const.NONE) {
            recallQuery.limit(limit, from);
        } else {
            recallQuery.limit(limit);
        }

        SelectQueryImpl countQuery = select().count(ENDPOINT_ID).from(client.getDatabase(), metricName);
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
                log.setContent((String) data.get(CONTENT));
                log.setContentType(ContentType.instanceOf(((Number) data.get(CONTENT_TYPE)).intValue()));

                log.setEndpointId((String) data.get(ENDPOINT_ID));
                log.setEndpointName((String) data.get(ENDPOINT_NAME));
                log.setTraceId((String) data.get(TRACE_ID));
                log.setTimestamp((String) data.get(TIMESTAMP));

                log.setStatusCode((String) data.get(STATUS_CODE));

                log.setServiceId((String) data.get(SERVICE_ID));
                log.setServiceInstanceId((String) data.get(SERVICE_INSTANCE_ID));

                logs.getLogs().add(log);
            });
        });

        return logs;
    }
}

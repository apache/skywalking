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
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.elasticsearch.common.Strings;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.influxdb.querybuilder.clauses.ConjunctionClause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.*;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public class LogQuery implements ILogQueryDAO {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final InfluxClient client;

    public LogQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public Logs queryLogs(String metricName, int serviceId, int serviceInstanceId, int endpointId, String traceId,
                          LogState state, String stateCode, Pagination paging, int from, int limit,
                          long startTB, long endTB) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query1 = select().all()
                .from(client.getDatabase(), metricName)
                .where();
        if (serviceId != Const.NONE) {
            query1.and(eq(SERVICE_ID, serviceId));
        }
        if (serviceInstanceId != Const.NONE) {
            query1.and(eq(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (endpointId != Const.NONE) {
            query1.and(eq(ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            query1.and(eq(TRACE_ID, traceId));
        }
        switch (state) {
            case ERROR: {
                query1.and(eq(IS_ERROR, true));
                break;
            }
            case SUCCESS: {
                query1.and(eq(IS_ERROR, false));
                break;
            }
        }
        if (!Strings.isNullOrEmpty(stateCode)) {
            query1.and(eq(STATUS_CODE, stateCode));
        }
        query1.and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB)))
                .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB)));
        if (from > Const.NONE) {
            limit += from;
            query1.limit(limit, from);
        } else {
            query1.limit(limit);
        }

        SelectQueryImpl query2 = select().count(ENDPOINT_ID).from(client.getDatabase(), metricName);
        for (ConjunctionClause clause : query1.getClauses()) {
            query2.where(clause);
        }

        Query query = new Query(query2.getCommand() + query1.getCommand());
        List<QueryResult.Result> results = client.query(query);
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: {} \nresult set: {}", query.getCommand(), results);
        }
        if (results.size() != 2) {
            throw new IOException("We expect to get 2 Results, but it is " + results.size());
        }

        final Logs logs = new Logs();
        QueryResult.Result counter = results.get(0);
        QueryResult.Result result = results.get(1);

        if (counter.hasError() || result.hasError()) {
            if (counter.hasError())
                throw new IOException(counter.getError());
            throw new IOException(result.getError());
        }

        List<QueryResult.Series> seriesList = results.get(1).getSeries();
        if (seriesList == null || seriesList.isEmpty()) {
            return logs;
        }

        logs.setTotal(((Number) counter.getSeries().get(0).getValues().get(0).get(1)).intValue());
        seriesList.forEach(series -> {
            final List<String> columns = series.getColumns();

            series.getValues().forEach(values -> {
                Map<String, Object> data = Maps.newHashMap();
                Log log = new Log();

                for (int i = 0; i < columns.size(); i++) {
                    data.put(columns.get(i), values.get(i));
                }
                log.setContent((String) data.get(CONTENT));
                log.setContentType(ContentType.instanceOf((int) data.get(CONTENT_TYPE)));

                log.setEndpointId((int) data.get(ENDPOINT_ID));
                log.setTraceId((String) data.get(TRACE_ID));
                log.setTimestamp((String) data.get(TIMESTAMP));

                log.setStatusCode((String) data.get(STATUS_CODE));

                log.setServiceId((int) data.get(SERVICE_ID));
                log.setServiceInstanceId((int) data.get(SERVICE_INSTANCE_ID));

                logs.getLogs().add(log);
            });
        });

        return logs;
    }
}

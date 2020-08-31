/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static java.util.Objects.nonNull;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class BrowserLogQuery implements IBrowserLogQueryDAO {
    private final InfluxClient client;

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(final String serviceId,
                                                  final String serviceVersionId,
                                                  final String pagePathId,
                                                  final String pagePath,
                                                  final BrowserErrorCategory category,
                                                  final long startSecondTB,
                                                  final long endSecondTB,
                                                  final int limit,
                                                  final int from) throws IOException {

        WhereQueryImpl<SelectQueryImpl> recallQuery = select()
            .function(InfluxConstants.SORT_DES, BrowserErrorLogRecord.TIMESTAMP, limit + from)
            .column(BrowserErrorLogRecord.DATA_BINARY)
            .from(
                client.getDatabase(),
                BrowserErrorLogRecord.INDEX_NAME
            ).where();
        if (startSecondTB != 0 && endSecondTB != 0) {
            recallQuery.and(gte(BrowserErrorLogRecord.TIME_BUCKET, startSecondTB))
                       .and(lte(BrowserErrorLogRecord.TIME_BUCKET, endSecondTB));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            recallQuery.and(eq(BrowserErrorLogRecord.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            recallQuery.and(eq(BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            recallQuery.and(eq(BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
        }
        if (nonNull(category)) {
            recallQuery.and(eq(BrowserErrorLogRecord.ERROR_CATEGORY, category.getValue()));
        }
        if (StringUtil.isNotEmpty(pagePath)) {
            recallQuery.and(contains(BrowserErrorLogRecord.PAGE_PATH, pagePath.replaceAll("/", "\\\\/")));
        }

        WhereQueryImpl<SelectQueryImpl> countQuery = select()
            .count(BrowserErrorLogRecord.SERVICE_ID)
            .from(client.getDatabase(), BrowserErrorLogRecord.INDEX_NAME)
            .where();
        recallQuery.getClauses().forEach(countQuery::where);

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
            return new BrowserErrorLogs();
        }

        BrowserErrorLogs logs = new BrowserErrorLogs();
        logs.setTotal(((Number) counter.get(0).getValues().get(0).get(1)).intValue());

        result.get(0).getValues().stream().sorted((a, b) -> {
            // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
            return Long.compare(((Number) b.get(1)).longValue(), ((Number) a.get(1)).longValue());
        }).skip(from).forEach(values -> {
            String dataBinaryBase64 = (String) values.get(2);
            if (nonNull(dataBinaryBase64)) {
                BrowserErrorLog log = parserDataBinary(dataBinaryBase64);
                logs.getLogs().add(log);
            }
        });
        return logs;
    }
}

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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.elasticsearch.common.Strings;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
public class IoTDBBrowserLogQueryDAO implements IBrowserLogQueryDAO {
    private final IoTDBClient client;

    public IoTDBBrowserLogQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId,
                                                  String pagePath, BrowserErrorCategory category, long startSecondTB,
                                                  long endSecondTB, int limit, int from) throws IOException {
        // IoTDB doesn't support using "string_contains" and "count" together
        // IoTDB string_contains return the same size of all data with true or false
        // select all BrowserErrorLogRecord.DATA_BINARY with string_contains, then count its size and return its limit
        StringBuilder query = new StringBuilder();
        query.append("select");
        if (Strings.isNullOrEmpty(pagePath)) {
            query.append(" string_contains(").append(BrowserErrorLogRecord.PAGE_PATH).append(", 's'='").append(pagePath).append("'),");
        }
        query.append(" ").append(BrowserErrorLogRecord.DATA_BINARY);
        query.append(" from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(BrowserErrorLogRecord.INDEX_NAME)
                .append(" where 1=1 ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            query.append(" and ").append(BrowserErrorLogRecord.TIME_BUCKET).append(" >= ").append(startSecondTB);
            query.append(" and ").append(BrowserErrorLogRecord.TIME_BUCKET).append(" <= ").append(endSecondTB);
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            query.append(" and ").append(BrowserErrorLogRecord.SERVICE_ID).append(" = '").append(serviceId).append("'");
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            query.append(" and ").append(BrowserErrorLogRecord.SERVICE_VERSION_ID).append(" = '").append(serviceVersionId).append("'");
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            query.append(" and ").append(BrowserErrorLogRecord.PAGE_PATH_ID).append(" = '").append(pagePathId).append("'");
        }
        if (Objects.nonNull(category)) {
            query.append(" and ").append(BrowserErrorLogRecord.ERROR_CATEGORY).append(" = ").append(category.getValue());
        }

        BrowserErrorLogs logs = new BrowserErrorLogs();
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(client.getStorageGroup()).append(IoTDBClient.DOT).append(BrowserErrorLogRecord.INDEX_NAME);
        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper;
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return logs;
            }
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            int count = 0;
            List<String> iotDBColumnNames = wrapper.getColumnNames();
            if (iotDBColumnNames.get(1).startsWith("string_contains")) {
                while (wrapper.hasNext()) {
                    RowRecord rowRecord = wrapper.next();
                    List<Field> fieldList = rowRecord.getFields();
                    if (fieldList.get(1).getBoolV()) {
                        if (count >= from && logs.getLogs().size() <= limit) {
                            BrowserErrorLog log = parserDataBinary(fieldList.get(2).getStringValue());
                            logs.getLogs().add(log);
                        }
                        count++;
                    }
                }
            } else {
                while (wrapper.hasNext()) {
                    RowRecord rowRecord = wrapper.next();
                    List<Field> fieldList = rowRecord.getFields();
                    if (count >= from && logs.getLogs().size() <= limit) {
                        BrowserErrorLog log = parserDataBinary(fieldList.get(1).getStringValue());
                        logs.getLogs().add(log);
                    }
                    count++;
                }
            }
            logs.setTotal(count);
            return logs;
        } catch (IoTDBConnectionException |
                StatementExecutionException e) {
            throw new IOException(e);
        }
    }
}

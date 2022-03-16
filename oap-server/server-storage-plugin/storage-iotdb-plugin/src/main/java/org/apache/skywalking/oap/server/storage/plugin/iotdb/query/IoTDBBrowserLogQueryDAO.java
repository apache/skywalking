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

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.query.type.ErrorCategory;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

@Slf4j
@RequiredArgsConstructor
public class IoTDBBrowserLogQueryDAO implements IBrowserLogQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<BrowserErrorLogRecord> storageBuilder = new BrowserErrorLogRecord.Builder();

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId,
                                                  BrowserErrorCategory category, long startSecondTB,
                                                  long endSecondTB, int limit, int from) throws IOException {
        StringBuilder query = new StringBuilder();
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query.append("select * from ");
        query = client.addModelPath(query, BrowserErrorLogRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(serviceId)) {
            indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        }
        query = client.addQueryIndexValue(BrowserErrorLogRecord.INDEX_NAME, query, indexAndValueMap);

        StringBuilder where = new StringBuilder(" where ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            where.append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startSecondTB)).append(" and ");
            where.append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endSecondTB)).append(" and ");
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            where.append(BrowserErrorLogRecord.SERVICE_VERSION_ID).append(" = \"").append(serviceVersionId).append("\"").append(" and ");
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            where.append(BrowserErrorLogRecord.PAGE_PATH_ID).append(" = \"").append(pagePathId).append("\"").append(" and ");
        }
        if (Objects.nonNull(category)) {
            where.append(BrowserErrorLogRecord.ERROR_CATEGORY).append(" = ").append(category.getValue()).append(" and ");
        }
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(BrowserErrorLogRecord.INDEX_NAME, query.toString(), storageBuilder);
        List<BrowserErrorLogRecord> browserErrorLogRecordList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> browserErrorLogRecordList.add((BrowserErrorLogRecord) storageData));
        // resort by self, because of the select query result order by time.
        browserErrorLogRecordList.sort((BrowserErrorLogRecord b1, BrowserErrorLogRecord b2) -> Long.compare(b2.getTimestamp(), b1.getTimestamp()));
        BrowserErrorLogs logs = new BrowserErrorLogs();
        int limitCount = 0;
        for (int i = from; i < browserErrorLogRecordList.size(); i++) {
            if (limitCount < limit) {
                limitCount++;
                BrowserErrorLogRecord record = browserErrorLogRecordList.get(i);
                if (CollectionUtils.isNotEmpty(record.getDataBinary())) {
                    BrowserErrorLog log = iotdbParserDataBinary(record.getDataBinary());
                    logs.getLogs().add(log);
                }
            }
        }
        logs.setTotal(storageDataList.size());
        return logs;
    }

    private BrowserErrorLog iotdbParserDataBinary(byte[] dataBinaryBase64) {
        try {
            BrowserErrorLog log = new BrowserErrorLog();
            org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog browserErrorLog = org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog
                    .parseFrom(dataBinaryBase64);
            log.setService(browserErrorLog.getService());
            log.setServiceVersion(browserErrorLog.getServiceVersion());
            log.setTime(browserErrorLog.getTime());
            log.setPagePath(browserErrorLog.getPagePath());
            log.setCategory(ErrorCategory.valueOf(browserErrorLog.getCategory().name().toUpperCase()));
            log.setGrade(browserErrorLog.getGrade());
            log.setMessage(browserErrorLog.getMessage());
            log.setLine(browserErrorLog.getLine());
            log.setCol(browserErrorLog.getCol());
            log.setStack(browserErrorLog.getStack());
            log.setErrorUrl(browserErrorLog.getErrorUrl());
            log.setFirstReportedError(browserErrorLog.getFirstReportedError());

            return log;
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}

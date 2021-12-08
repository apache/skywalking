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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.query.type.ErrorCategory;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord} is a stream
 */
public class BanyanDBBrowserLogQueryDAO extends AbstractBanyanDBDAO implements IBrowserLogQueryDAO {
    public BanyanDBBrowserLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId, BrowserErrorCategory category, long startSecondTB, long endSecondTB, int limit, int from) throws IOException {
        TimestampRange tsRange = null;
        if (startSecondTB > 0 && endSecondTB > 0) {
            tsRange = new TimestampRange(TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        }

        final BrowserErrorLogs logs = new BrowserErrorLogs();
        StreamQueryResponse resp = query(BrowserErrorLogRecord.INDEX_NAME, ImmutableList.of(BrowserErrorLogRecord.SERVICE_ID,
                BrowserErrorLogRecord.SERVICE_VERSION_ID,
                BrowserErrorLogRecord.PAGE_PATH_ID,
                BrowserErrorLogRecord.ERROR_CATEGORY), tsRange, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.setDataProjections(Collections.singletonList(BrowserErrorLogRecord.DATA_BINARY));
                query.appendCondition(eq(BrowserErrorLogRecord.SERVICE_ID, serviceId));

                if (StringUtil.isNotEmpty(serviceVersionId)) {
                    query.appendCondition(eq(BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
                }

                if (StringUtil.isNotEmpty(pagePathId)) {
                    query.appendCondition(eq(BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
                }

                if (Objects.nonNull(category)) {
                    query.appendCondition(eq(BrowserErrorLogRecord.ERROR_CATEGORY, category.getValue()));
                }

                query.setOffset(from);
                query.setLimit(limit);
            }
        });
        logs.getLogs().addAll(resp.getElements().stream().map(new BrowserErrorLogDeserializer()).collect(Collectors.toList()));
        logs.setTotal(logs.getLogs().size());
        return logs;
    }

    public static class BrowserErrorLogDeserializer implements RowEntityDeserializer<BrowserErrorLog> {
        @Override
        public BrowserErrorLog apply(RowEntity row) {
            // FIXME: use protobuf directly
            BrowserErrorLog log = new BrowserErrorLog();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            log.setService((String) searchable.get(0).getValue());
            log.setServiceVersion((String) searchable.get(1).getValue());
            log.setPagePath((String) searchable.get(2).getValue());
            log.setCategory(ErrorCategory.valueOf((String) searchable.get(3).getValue()));
            log.setTime(row.getTimestamp());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            Object o = data.get(0).getValue();
            if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
                try {
                    org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog browserErrorLog = org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog
                            .parseFrom((ByteString) o);
                    log.setGrade(browserErrorLog.getGrade());
                    log.setCol(browserErrorLog.getCol());
                    log.setLine(browserErrorLog.getLine());
                    log.setMessage(browserErrorLog.getMessage());
                    log.setErrorUrl(browserErrorLog.getErrorUrl());
                    log.setStack(browserErrorLog.getStack());
                    log.setFirstReportedError(browserErrorLog.getFirstReportedError());
                } catch (InvalidProtocolBufferException ex) {
                    throw new RuntimeException("fail to parse proto buffer", ex);
                }
            }
            return log;
        }
    }
}

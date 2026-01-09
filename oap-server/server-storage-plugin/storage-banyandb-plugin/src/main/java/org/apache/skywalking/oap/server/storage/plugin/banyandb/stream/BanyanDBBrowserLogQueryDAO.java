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

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * {@link org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord} is a stream
 */
public class BanyanDBBrowserLogQueryDAO extends AbstractBanyanDBDAO implements IBrowserLogQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(BrowserErrorLogRecord.SERVICE_ID,
            BrowserErrorLogRecord.SERVICE_VERSION_ID, BrowserErrorLogRecord.PAGE_PATH_ID,
            BrowserErrorLogRecord.ERROR_CATEGORY, BrowserErrorLogRecord.DATA_BINARY);

    public BanyanDBBrowserLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId,
                                                  BrowserErrorCategory category, Duration duration,
                                                  int limit, int from) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        StreamQueryResponse resp = query(isColdStage, BrowserErrorLogRecord.INDEX_NAME, TAGS,
                getTimestampRange(duration), new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(BrowserErrorLogRecord.SERVICE_ID, serviceId));
                        }

                        if (StringUtil.isNotEmpty(serviceVersionId)) {
                            query.and(eq(BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
                        }

                        if (StringUtil.isNotEmpty(pagePathId)) {
                            query.and(eq(BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
                        }

                        if (Objects.nonNull(category)) {
                            query.and(eq(BrowserErrorLogRecord.ERROR_CATEGORY, category.getValue()));
                        }

                        query.setOffset(from);
                        query.setLimit(limit);
                    }
                });

        BrowserErrorLogs logs = new BrowserErrorLogs();

        for (final RowEntity rowEntity : resp.getElements()) {
            final byte[] dataBinary = rowEntity.getTagValue(BrowserErrorLogRecord.DATA_BINARY);
            if (dataBinary != null && dataBinary.length > 0) {
                BrowserErrorLog log = parserDataBinary(dataBinary);
                logs.getLogs().add(log);
            }
        }
        return logs;
    }
}

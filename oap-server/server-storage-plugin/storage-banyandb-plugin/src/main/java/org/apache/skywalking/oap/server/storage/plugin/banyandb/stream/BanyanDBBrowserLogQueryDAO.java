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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * {@link org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord} is a stream
 */
public class BanyanDBBrowserLogQueryDAO extends AbstractBanyanDBDAO implements IBrowserLogQueryDAO {
    public BanyanDBBrowserLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId, BrowserErrorCategory category, long startSecondTB, long endSecondTB, int limit, int from) throws IOException {

        final QueryBuilder qb = new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.SERVICE_ID, serviceId));

                if (StringUtil.isNotEmpty(serviceVersionId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
                }
                if (StringUtil.isNotEmpty(pagePathId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
                }
                if (Objects.nonNull(category)) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", BrowserErrorLogRecord.ERROR_CATEGORY, (long) category.getValue()));
                }

                query.setOffset(from);
                query.setLimit(limit);
            }
        };

        final BrowserErrorLogs logs = new BrowserErrorLogs();
        final List<BrowserErrorLog> browserErrorLogs;
        if (startSecondTB != 0 && endSecondTB != 0) {
            browserErrorLogs = query(BrowserErrorLog.class, qb, TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        } else {
            browserErrorLogs = query(BrowserErrorLog.class, qb);
        }
        logs.getLogs().addAll(browserErrorLogs);
        logs.setTotal(logs.getLogs().size());
        return logs;
    }
}

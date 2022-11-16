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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import static java.util.Objects.nonNull;

public class BrowserLogQueryEsDAO extends EsDAO implements IBrowserLogQueryDAO {
    public BrowserLogQueryEsDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(final String serviceId,
                                                  final String serviceVersionId,
                                                  final String pagePathId,
                                                  final BrowserErrorCategory category,
                                                  final Duration duration,
                                                  final int limit,
                                                  final int from) throws IOException {
        long startSecondTB = 0, endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        final BoolQueryBuilder boolQueryBuilder = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(BrowserErrorLogRecord.INDEX_NAME)) {
            boolQueryBuilder.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, BrowserErrorLogRecord.INDEX_NAME));
        }

        if (startSecondTB != 0 && endSecondTB != 0) {
            boolQueryBuilder.must(
                Query.range(BrowserErrorLogRecord.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            boolQueryBuilder.must(
                Query.term(BrowserErrorLogRecord.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            boolQueryBuilder.must(
                Query.term(BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            boolQueryBuilder.must(
                Query.term(BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
        }
        if (nonNull(category)) {
            boolQueryBuilder.must(
                Query.term(BrowserErrorLogRecord.ERROR_CATEGORY, category.getValue()));
        }

        final SearchBuilder sourceBuilder =
            Search.builder()
                  .query(boolQueryBuilder)
                  .sort(BrowserErrorLogRecord.TIMESTAMP, Sort.Order.DESC)
                  .size(limit)
                  .from(from);
        final SearchResponse response = getClient()
            .search(
                IndexController.LogicIndicesRegister.getPhysicalTableName(BrowserErrorLogRecord.INDEX_NAME),
                sourceBuilder.build()
            );

        BrowserErrorLogs logs = new BrowserErrorLogs();

        for (SearchHit searchHit : response.getHits().getHits()) {
            final String dataBinaryBase64 =
                (String) searchHit.getSource().get(BrowserErrorLogRecord.DATA_BINARY);
            if (nonNull(dataBinaryBase64)) {
                BrowserErrorLog log = parserDataBinary(dataBinaryBase64);
                logs.getLogs().add(log);
            }
        }
        return logs;
    }
}

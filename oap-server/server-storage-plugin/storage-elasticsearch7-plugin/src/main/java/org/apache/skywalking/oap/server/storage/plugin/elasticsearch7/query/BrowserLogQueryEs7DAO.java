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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import com.google.common.base.Strings;
import java.io.IOException;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.BrowserLogQueryEsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static java.util.Objects.nonNull;

public class BrowserLogQueryEs7DAO extends BrowserLogQueryEsDAO {
    public BrowserLogQueryEs7DAO(final ElasticSearchClient client) {
        super(client);
    }

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
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        if (startSecondTB != 0 && endSecondTB != 0) {
            boolQueryBuilder.must().add(
                QueryBuilders.rangeQuery(BrowserErrorLogRecord.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }

        if (!Strings.isNullOrEmpty(pagePath)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(BrowserErrorLogRecord.PAGE_PATH);
            boolQueryBuilder.must().add(QueryBuilders.matchPhraseQuery(matchCName, pagePath));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(BrowserErrorLogRecord.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
        }
        if (nonNull(category)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(BrowserErrorLogRecord.ERROR_CATEGORY, category.getValue()));
        }
        sourceBuilder.size(limit);
        sourceBuilder.from(from);

        SearchResponse response = getClient().search(BrowserErrorLogRecord.INDEX_NAME, sourceBuilder);

        BrowserErrorLogs logs = new BrowserErrorLogs();
        logs.setTotal((int) response.getHits().getTotalHits().value);

        for (SearchHit searchHit : response.getHits().getHits()) {
            String dataBinaryBase64 = (String) searchHit.getSourceAsMap().get(BrowserErrorLogRecord.DATA_BINARY);
            if (nonNull(dataBinaryBase64)) {
                BrowserErrorLog log = parserDataBinary(dataBinaryBase64);
                logs.getLogs().add(log);
            }
        }
        return logs;
    }
}

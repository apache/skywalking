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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpanAttachedEventEsDAO extends EsDAO implements ISpanAttachedEventQueryDAO {
    private final int scrollingBatchSize;

    public SpanAttachedEventEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.scrollingBatchSize = config.getProfileDataQueryBatchSize();
    }

    @Override
    public List<SpanAttachedEventRecord> querySpanAttachedEvents(SpanAttachedEventTraceType type, List<String> traceIds) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(SpanAttachedEventRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(SpanAttachedEventRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, SpanAttachedEventRecord.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(scrollingBatchSize);
        query.must(Query.terms(SpanAttachedEventRecord.RELATED_TRACE_ID, traceIds));
        query.must(Query.terms(SpanAttachedEventRecord.TRACE_REF_TYPE, type.value()));
        search.sort(SpanAttachedEventRecord.START_TIME_SECOND, Sort.Order.ASC);
        search.sort(SpanAttachedEventRecord.START_TIME_NANOS, Sort.Order.ASC);

        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        final List<SpanAttachedEventRecord> records = new ArrayList<>();

        SearchResponse results = getClient().search(index, search.build(), params);
        final Set<String> scrollIds = new HashSet<>();
        try {
            while (true) {
                final String scrollId = results.getScrollId();
                scrollIds.add(scrollId);
                if (results.getHits().getTotal() == 0) {
                    break;
                }
                final List<SpanAttachedEventRecord> batch = buildDataList(results);
                records.addAll(batch);
                // The last iterate, there is no more data
                if (batch.size() < scrollingBatchSize) {
                    break;
                }
                results = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(getClient()::deleteScrollContextQuietly);
        }
        return records;
    }

    private List<SpanAttachedEventRecord> buildDataList(SearchResponse response) {
        final ArrayList<SpanAttachedEventRecord> records = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            final Map<String, Object> sourceAsMap = hit.getSource();
            final SpanAttachedEventRecord.Builder builder = new SpanAttachedEventRecord.Builder();
            records.add(builder.storage2Entity(new ElasticSearchConverter.ToEntity(SpanAttachedEventRecord.INDEX_NAME, sourceAsMap)));
        }
        return records;
    }
}
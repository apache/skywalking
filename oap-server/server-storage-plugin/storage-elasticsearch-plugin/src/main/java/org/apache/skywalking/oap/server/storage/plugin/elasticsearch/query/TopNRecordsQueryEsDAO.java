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

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

/**
 * @author wusheng
 */
public class TopNRecordsQueryEsDAO extends EsDAO implements ITopNRecordsQueryDAO {
    public TopNRecordsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<TopNRecord> getTopNRecords(long startSecondTB, long endSecondTB, String metricName, int serviceId,
        int topN, Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(TopN.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(TopN.SERVICE_ID, serviceId));

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(topN).sort(TopN.LATENCY, order.equals(Order.DES) ? SortOrder.DESC : SortOrder.ASC);
        SearchResponse response = getClient().search(metricName, sourceBuilder);

        List<TopNRecord> results = new ArrayList<>();

        for (SearchHit searchHit : response.getHits().getHits()) {
            TopNRecord record = new TopNRecord();
            record.setStatement((String)searchHit.getSourceAsMap().get(TopN.STATEMENT));
            record.setTraceId((String)searchHit.getSourceAsMap().get(TopN.TRACE_ID));
            record.setLatency(((Number)searchHit.getSourceAsMap().get(TopN.LATENCY)).longValue());
            results.add(record);
        }

        return results;
    }
}

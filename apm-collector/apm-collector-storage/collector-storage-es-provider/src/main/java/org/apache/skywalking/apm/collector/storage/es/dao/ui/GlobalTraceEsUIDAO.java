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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceEsUIDAO extends EsDAO implements IGlobalTraceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsUIDAO.class);

    public GlobalTraceEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<String> getGlobalTraceId(String segmentId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GlobalTraceTable.TABLE);
        searchRequestBuilder.setTypes(GlobalTraceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(GlobalTraceTable.SEGMENT_ID.getName(), segmentId));
        searchRequestBuilder.setSize(10);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<String> globalTraceIds = new ArrayList<>();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        for (SearchHit searchHit : searchHits) {
            String globalTraceId = (String)searchHit.getSource().get(GlobalTraceTable.TRACE_ID.getName());
            logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
            globalTraceIds.add(globalTraceId);
        }
        return globalTraceIds;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GlobalTraceTable.TABLE);
        searchRequestBuilder.setTypes(GlobalTraceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(GlobalTraceTable.TRACE_ID.getName(), globalTraceId));
        searchRequestBuilder.setSize(10);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        List<String> segmentIds = new ArrayList<>();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        for (SearchHit searchHit : searchHits) {
            String segmentId = (String)searchHit.getSource().get(GlobalTraceTable.SEGMENT_ID.getName());
            logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
            segmentIds.add(segmentId);
        }
        return segmentIds;
    }
}

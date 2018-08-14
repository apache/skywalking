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

import java.util.*;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.elasticsearch.action.search.*;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class ServiceNameServiceEsUIDAO extends EsDAO implements IServiceNameServiceUIDAO {

    public ServiceNameServiceEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getCount(long startTimeMillis) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), SpanType.Entry_VALUE));
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceNameTable.HEARTBEAT_TIME.getName()).gte(startTimeMillis));
        searchRequestBuilder.setQuery(boolQuery);

        searchRequestBuilder.setSize(0);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return (int)searchResponse.getHits().getTotalHits();
    }

    @Override
    public List<ServiceInfo> searchService(String keyword, int applicationId, long startTimeMillis, int topN) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(topN);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), SpanType.Entry_VALUE));
        boolQuery.must().add(QueryBuilders.rangeQuery(ServiceNameTable.HEARTBEAT_TIME.getName()).gte(startTimeMillis));

        if (applicationId != Const.NONE) {
            boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.APPLICATION_ID.getName(), applicationId));
        }

        if (StringUtils.isNotEmpty(keyword)) {
            boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.SERVICE_NAME.getName(), keyword));
        }
        searchRequestBuilder.setQuery(boolQuery);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        List<ServiceInfo> serviceInfos = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setId(((Number)searchHit.getSource().get(ServiceNameTable.SERVICE_ID.getName())).intValue());
            serviceInfo.setName((String)searchHit.getSource().get(ServiceNameTable.SERVICE_NAME.getName()));
            serviceInfo.setApplicationId(((Number)searchHit.getSource().get(ServiceNameTable.APPLICATION_ID.getName())).intValue());
            serviceInfos.add(serviceInfo);
        }
        return serviceInfos;
    }
}

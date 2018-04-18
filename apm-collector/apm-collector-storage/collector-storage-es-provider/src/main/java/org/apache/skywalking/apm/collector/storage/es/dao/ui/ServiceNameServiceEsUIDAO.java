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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class ServiceNameServiceEsUIDAO extends EsDAO implements IServiceNameServiceUIDAO {

    public ServiceNameServiceEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getCount() {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), SpanType.Entry_VALUE));
        searchRequestBuilder.setSize(0);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return (int)searchResponse.getHits().getTotalHits();
    }

    @Override public List<ServiceInfo> searchService(String keyword, int topN) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(topN);

        if (StringUtils.isNotEmpty(keyword)) {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.SERVICE_NAME.getName(), keyword));
            boolQuery.must().add(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), SpanType.Entry_VALUE));
            searchRequestBuilder.setQuery(boolQuery);
        } else {
            searchRequestBuilder.setQuery(QueryBuilders.termQuery(ServiceNameTable.SRC_SPAN_TYPE.getName(), SpanType.Entry_VALUE));
        }

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        List<ServiceInfo> serviceInfos = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setId(((Number)searchHit.getSource().get(ServiceNameTable.SERVICE_ID.getName())).intValue());
            serviceInfo.setName((String)searchHit.getSource().get(ServiceNameTable.SERVICE_NAME.getName()));
            serviceInfos.add(serviceInfo);
        }
        return serviceInfos;
    }
}

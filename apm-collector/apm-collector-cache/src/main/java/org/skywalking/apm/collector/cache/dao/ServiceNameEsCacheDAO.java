/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.cache.dao;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author peng-yongsheng
 */
public class ServiceNameEsCacheDAO extends EsDAO implements IServiceNameCacheDAO {

    @Override public String getServiceName(int serviceId) {
        GetRequestBuilder getRequestBuilder = getClient().prepareGet(ServiceNameTable.TABLE, String.valueOf(serviceId));

        GetResponse getResponse = getRequestBuilder.get();
        if (getResponse.isExists()) {
            String serviceName = (String)getResponse.getSource().get(ServiceNameTable.COLUMN_SERVICE_NAME);
            int applicationId = ((Number)getResponse.getSource().get(ServiceNameTable.COLUMN_APPLICATION_ID)).intValue();
            return applicationId + Const.ID_SPLIT + serviceName;
        }
        return Const.EMPTY_STRING;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceNameTable.TABLE);
        searchRequestBuilder.setTypes(ServiceNameTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.matchQuery(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName));
        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.get();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(ServiceNameTable.COLUMN_SERVICE_ID);
        }
        return 0;
    }
}

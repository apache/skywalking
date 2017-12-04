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

package org.skywalking.apm.collector.storage.es.dao;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.IInstanceCacheDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceEsCacheDAO extends EsDAO implements IInstanceCacheDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsCacheDAO.class);

    public InstanceEsCacheDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getApplicationId(int instanceId) {
        GetResponse response = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId)).get();
        if (response.isExists()) {
            return (int)response.getSource().get(InstanceTable.COLUMN_APPLICATION_ID);
        } else {
            return 0;
        }
    }

    @Override public int getInstanceId(int applicationId, String agentUUID) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        builder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_AGENT_UUID, agentUUID));
        searchRequestBuilder.setQuery(builder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            return (int)searchHit.getSource().get(InstanceTable.COLUMN_INSTANCE_ID);
        }
        return 0;
    }
}

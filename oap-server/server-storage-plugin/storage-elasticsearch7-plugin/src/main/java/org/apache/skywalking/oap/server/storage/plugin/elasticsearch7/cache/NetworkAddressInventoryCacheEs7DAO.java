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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.cache;

import org.apache.skywalking.oap.server.core.register.NetworkAddressInventory;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressInventoryCacheEsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, jian.tan
 * @author kezhenxu94
 */
public class NetworkAddressInventoryCacheEs7DAO extends NetworkAddressInventoryCacheEsDAO {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressInventoryCacheEs7DAO.class);

    public NetworkAddressInventoryCacheEs7DAO(ElasticSearchClient client, int resultWindowMaxSize) {
        super(client, resultWindowMaxSize);
    }

    @Override public NetworkAddressInventory get(int addressId) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery(NetworkAddressInventory.SEQUENCE, addressId));
            searchSourceBuilder.size(1);

            SearchResponse response = getClient().search(NetworkAddressInventory.INDEX_NAME, searchSourceBuilder);
            if (response.getHits().getTotalHits().value == 1) {
                SearchHit searchHit = response.getHits().getAt(0);
                return builder.map2Data(searchHit.getSourceAsMap());
            } else {
                return null;
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return null;
        }
    }
}

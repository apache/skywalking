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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class EndpointInventoryCacheEsDAO extends EsDAO implements IEndpointInventoryCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(EndpointInventoryCacheEsDAO.class);

    protected final EndpointInventory.Builder builder = new EndpointInventory.Builder();

    public EndpointInventoryCacheEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public int getEndpointId(int serviceId, String endpointName, int detectPoint) {
        try {
            String id = EndpointInventory.buildId(serviceId, endpointName, detectPoint);
            GetResponse response = getClient().get(EndpointInventory.INDEX_NAME, id);
            if (response.isExists()) {
                return (int)response.getSource().getOrDefault(RegisterSource.SEQUENCE, 0);
            } else {
                return Const.NONE;
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return Const.NONE;
        }
    }

    @Override public EndpointInventory get(int endpointId) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery(EndpointInventory.SEQUENCE, endpointId));
            searchSourceBuilder.size(1);

            SearchResponse response = getClient().search(EndpointInventory.INDEX_NAME, searchSourceBuilder);
            if (response.getHits().totalHits == 1) {
                SearchHit searchHit = response.getHits().getAt(0);
                return builder.map2Data(searchHit.getSourceAsMap());
            } else {
                return null;
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}

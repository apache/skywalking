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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class EsDAO extends AbstractDAO<ElasticSearchClient> {

    private static final Logger logger = LoggerFactory.getLogger(EsDAO.class);

    public EsDAO(ElasticSearchClient client) {
        super(client);
    }

    protected final int getMaxId(String indexName, String columnName) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.max("agg").field(columnName));
        searchSourceBuilder.size(0);
        return getResponse(indexName, searchSourceBuilder);
    }

    protected final int getMinId(String indexName, String columnName) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.min("agg").field(columnName));
        searchSourceBuilder.size(0);
        return getResponse(indexName, searchSourceBuilder);
    }

    private int getResponse(String indexName, SearchSourceBuilder searchSourceBuilder) {
        try {
            SearchResponse searchResponse = getClient().search(indexName, searchSourceBuilder);
            Max agg = searchResponse.getAggregations().get("agg");

            int id = (int)agg.getValue();
            if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
                return 0;
            } else {
                return id;
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}

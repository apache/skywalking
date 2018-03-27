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

package org.apache.skywalking.apm.collector.storage.es.base.dao;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.base.dao.AbstractDAO;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;

/**
 * @author peng-yongsheng
 */
public abstract class EsDAO extends AbstractDAO<ElasticSearchClient> {

    public EsDAO(ElasticSearchClient client) {
        super(client);
    }

    protected final int getMaxId(String indexName, String columnName) {
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MaxAggregationBuilder aggregation = AggregationBuilders.max("agg").field(columnName);
        searchRequestBuilder.addAggregation(aggregation);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Max agg = searchResponse.getAggregations().get("agg");

        int id = (int)agg.getValue();
        if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
            return 0;
        } else {
            return id;
        }
    }

    protected final int getMinId(String indexName, String columnName) {
        ElasticSearchClient client = getClient();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSize(0);
        MinAggregationBuilder aggregation = AggregationBuilders.min("agg").field(columnName);
        searchRequestBuilder.addAggregation(aggregation);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Min agg = searchResponse.getAggregations().get("agg");

        int id = (int)agg.getValue();
        if (id == Integer.MAX_VALUE || id == Integer.MIN_VALUE) {
            return 0;
        } else {
            return id;
        }
    }
}

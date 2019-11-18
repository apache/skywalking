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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.TopNEntity;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AggregationQueryEsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class AggregationQueryEs7DAO extends AggregationQueryEsDAO {

    public AggregationQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    protected List<TopNEntity> aggregation(
        String indexName,
        String valueCName,
        SearchSourceBuilder sourceBuilder,
        int topN,
        Order order) throws IOException {

        boolean asc = false;
        if (order.equals(Order.ASC)) {
            asc = true;
        }

        TermsAggregationBuilder aggregationBuilder = aggregationBuilder(valueCName, topN, asc);

        sourceBuilder.aggregation(aggregationBuilder);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<TopNEntity> topNEntities = new ArrayList<>();
        Terms idTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket termsBucket : idTerms.getBuckets()) {
            TopNEntity topNEntity = new TopNEntity();
            topNEntity.setId(termsBucket.getKeyAsString());
            Avg value = termsBucket.getAggregations().get(valueCName);
            topNEntity.setValue((long) value.getValue());
            topNEntities.add(topNEntity);
        }

        return topNEntities;
    }

    protected TermsAggregationBuilder aggregationBuilder(final String valueCName, final int topN, final boolean asc) {
        return AggregationBuilders
            .terms(Metrics.ENTITY_ID)
            .field(Metrics.ENTITY_ID)
            .order(BucketOrder.aggregation(valueCName, asc))
            .size(topN)
            .subAggregation(
                AggregationBuilders.avg(valueCName).field(valueCName)
            );
    }
}

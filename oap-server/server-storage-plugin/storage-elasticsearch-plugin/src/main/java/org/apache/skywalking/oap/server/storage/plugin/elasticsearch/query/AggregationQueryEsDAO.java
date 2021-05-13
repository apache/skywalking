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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class AggregationQueryEsDAO extends EsDAO implements IAggregationQueryDAO {

    public AggregationQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<SelectedRecord> sortMetrics(final TopNCondition condition,
                                            final String valueColumnName,
                                            final Duration duration,
                                            final List<KeyValue> additionalConditions) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        final RangeQueryBuilder queryBuilder = QueryBuilders.rangeQuery(Metrics.TIME_BUCKET)
                                                            .lte(duration.getEndTimeBucket())
                                                            .gte(duration.getStartTimeBucket());

        boolean asc = false;
        if (condition.getOrder().equals(Order.ASC)) {
            asc = true;
        }
        String tableName = IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());

        if (CollectionUtils.isEmpty(additionalConditions)
            && IndexController.LogicIndicesRegister.isMetricTable(condition.getName())) {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must()
                     .add(QueryBuilders.termQuery(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ));
            boolQuery.must().add(queryBuilder);
            sourceBuilder.query(boolQuery);
        } else if (CollectionUtils.isEmpty(additionalConditions)) {
            sourceBuilder.query(queryBuilder);
        } else if (CollectionUtils.isNotEmpty(additionalConditions)
            && IndexController.LogicIndicesRegister.isMetricTable(condition.getName())) {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must()
                     .add(QueryBuilders.termQuery(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ));
            additionalConditions.forEach(additionalCondition -> boolQuery
                .must()
                .add(QueryBuilders.termsQuery(additionalCondition.getKey(), additionalCondition.getValue())));
            boolQuery.must().add(queryBuilder);
            sourceBuilder.query(boolQuery);
        } else {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            additionalConditions.forEach(additionalCondition -> boolQuery
                .must()
                .add(QueryBuilders.termsQuery(additionalCondition.getKey(), additionalCondition.getValue())));
            boolQuery.must().add(queryBuilder);
            sourceBuilder.query(boolQuery);
        }

        sourceBuilder.aggregation(
            AggregationBuilders.terms(Metrics.ENTITY_ID)
                               .field(Metrics.ENTITY_ID)
                               .order(BucketOrder.aggregation(valueColumnName, asc))
                               .size(condition.getTopN())
                               .subAggregation(AggregationBuilders.avg(valueColumnName).field(valueColumnName))
        );

        SearchResponse response = getClient().search(tableName, sourceBuilder);

        List<SelectedRecord> topNList = new ArrayList<>();
        Terms idTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket termsBucket : idTerms.getBuckets()) {
            SelectedRecord record = new SelectedRecord();
            record.setId(termsBucket.getKeyAsString());
            Avg value = termsBucket.getAggregations().get(valueColumnName);
            record.setValue(String.valueOf((long) value.getValue()));
            topNList.add(record);
        }

        return topNList;
    }

}

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

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.RangeQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.BucketOrder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
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
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AggregationQueryEsDAO extends EsDAO implements IAggregationQueryDAO {

    public AggregationQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<SelectedRecord> sortMetrics(final TopNCondition condition,
                                            final String valueColumnName,
                                            final Duration duration,
                                            final List<KeyValue> additionalConditions) {
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final RangeQueryBuilder basicQuery = Query.range(Metrics.TIME_BUCKET)
                                                  .lte(duration.getEndTimeBucket())
                                                  .gte(duration.getStartTimeBucket());
        final SearchBuilder search = Search.builder();

        final boolean asc = condition.getOrder().equals(Order.ASC);

        if (CollectionUtils.isEmpty(additionalConditions)
            && IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
            final BoolQueryBuilder boolQuery =
                Query.bool()
                     .must(basicQuery)
                     .must(Query.term(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ));
            search.query(boolQuery);
        } else if (CollectionUtils.isEmpty(additionalConditions)) {
            search.query(basicQuery);
        } else if (CollectionUtils.isNotEmpty(additionalConditions)
            && IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
            final BoolQueryBuilder boolQuery =
                Query.bool()
                     .must(Query.term(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ));
            additionalConditions.forEach(additionalCondition -> boolQuery
                .must(Query.terms(
                    additionalCondition.getKey(),
                    additionalCondition.getValue()
                )));
            boolQuery.must(basicQuery);
            search.query(boolQuery);
        } else {
            final BoolQueryBuilder boolQuery = Query.bool();
            additionalConditions.forEach(additionalCondition -> boolQuery
                .must(Query.terms(
                    additionalCondition.getKey(),
                    additionalCondition.getValue()
                )));
            boolQuery.must(basicQuery);
            search.query(boolQuery);
        }

        search.aggregation(
            Aggregation.terms(Metrics.ENTITY_ID)
                       .field(Metrics.ENTITY_ID)
                       .order(BucketOrder.aggregation(realValueColumn, asc))
                       .size(condition.getTopN())
                       .subAggregation(Aggregation.avg(realValueColumn).field(realValueColumn))
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                       .build());

        final SearchResponse response = getClient().search(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName()),
            duration.getStartTimeBucketInSec(),
            duration.getEndTimeBucketInSec()), search.build());

        final List<SelectedRecord> topNList = new ArrayList<>();
        final Map<String, Object> idTerms =
            (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);
        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) idTerms.get("buckets");
        for (Map<String, Object> termsBucket : buckets) {
            SelectedRecord record = new SelectedRecord();
            record.setId((String) termsBucket.get("key"));
            Map<String, Object> value = (Map<String, Object>) termsBucket.get(realValueColumn);
            record.setValue(String.valueOf(((Number) value.get("value")).longValue()));
            topNList.add(record);
        }

        return topNList;
    }
}

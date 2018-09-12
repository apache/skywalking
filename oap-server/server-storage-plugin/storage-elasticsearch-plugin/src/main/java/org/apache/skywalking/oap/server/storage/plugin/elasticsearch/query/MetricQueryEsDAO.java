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
import java.util.*;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.sql.*;
import org.apache.skywalking.oap.server.core.storage.TimePyramidTableNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class MetricQueryEsDAO extends EsDAO implements IMetricQueryDAO {

    public MetricQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    public List<OneIdGroupValue> aggregation(String indName, Step step, long startTB,
        long endTB, Where where, String idCName, String valueCName, Function function) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        queryBuild(sourceBuilder, where, startTB, endTB);

        TermsAggregationBuilder aggIdCName1 = AggregationBuilders.terms(idCName).field(idCName).size(1000);
        functionAggregation(function, aggIdCName1, valueCName);

        sourceBuilder.aggregation(aggIdCName1);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<OneIdGroupValue> values = new ArrayList<>();
        Terms idTerms = response.getAggregations().get(idCName);
        for (Terms.Bucket idBucket : idTerms.getBuckets()) {
            Terms valueTerms = idBucket.getAggregations().get(valueCName);
            for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
                OneIdGroupValue value = new OneIdGroupValue();
                value.setId(idBucket.getKeyAsNumber().intValue());
                value.setValue(valueBucket.getKeyAsNumber());
                values.add(value);
            }
        }
        return values;
    }

    public List<TwoIdGroupValue> aggregation(String indName, Step step, long startTB,
        long endTB, Where where, String idCName1, String idCName2, String valueCName,
        Function function) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        queryBuild(sourceBuilder, where, startTB, endTB);

        sourceBuilder.aggregation(
            AggregationBuilders.terms(idCName1).field(idCName1).size(1000)
                .subAggregation(AggregationBuilders.terms(idCName2).field(idCName2).size(1000)
                    .subAggregation(AggregationBuilders.avg(valueCName).field(valueCName)))
        );

        TermsAggregationBuilder aggIdCName1 = AggregationBuilders.terms(idCName1).field(idCName1).size(1000);
        TermsAggregationBuilder aggIdCName2 = AggregationBuilders.terms(idCName2).field(idCName2).size(1000);
        aggIdCName1.subAggregation(aggIdCName2);
        functionAggregation(function, aggIdCName2, valueCName);

        sourceBuilder.aggregation(aggIdCName1);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<TwoIdGroupValue> values = new ArrayList<>();
        Terms id1Terms = response.getAggregations().get(idCName1);
        for (Terms.Bucket id1Bucket : id1Terms.getBuckets()) {
            Terms id2Terms = id1Bucket.getAggregations().get(idCName2);
            for (Terms.Bucket id2Bucket : id2Terms.getBuckets()) {
                Terms valueTerms = id1Bucket.getAggregations().get(valueCName);
                for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
                    TwoIdGroupValue value = new TwoIdGroupValue();
                    value.setId1(id1Bucket.getKeyAsNumber().intValue());
                    value.setId1(id2Bucket.getKeyAsNumber().intValue());
                    value.setValue(valueBucket.getKeyAsNumber());
                    values.add(value);
                }
            }
        }
        return values;
    }

    private void functionAggregation(Function function, TermsAggregationBuilder parentAggBuilder, String valueCName) {
        switch (function) {
            case Avg:
                parentAggBuilder.subAggregation(AggregationBuilders.avg(valueCName).field(valueCName));
                break;
            case Sum:
                parentAggBuilder.subAggregation(AggregationBuilders.sum(valueCName).field(valueCName));
                break;
        }
    }
}

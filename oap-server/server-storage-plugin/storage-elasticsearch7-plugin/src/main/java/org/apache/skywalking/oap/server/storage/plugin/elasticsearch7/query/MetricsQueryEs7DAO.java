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

import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.KVInt;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetricsQueryEsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class MetricsQueryEs7DAO extends MetricsQueryEsDAO {

    public MetricsQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public IntValues getValues(
        String indName,
        Downsampling downsampling,
        long startTB,
        long endTB,
        Where where,
        String valueCName,
        Function function) throws IOException {

        String indexName = ModelName.build(downsampling, indName);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        queryBuild(sourceBuilder, where, startTB, endTB);

        TermsAggregationBuilder entityIdAggregation = AggregationBuilders.terms(Metrics.ENTITY_ID).field(Metrics.ENTITY_ID).size(1000);
        functionAggregation(function, entityIdAggregation, valueCName);

        sourceBuilder.aggregation(entityIdAggregation);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        IntValues intValues = new IntValues();
        Terms idTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket idBucket : idTerms.getBuckets()) {
            long value;
            switch (function) {
                case Sum:
                    Sum sum = idBucket.getAggregations().get(valueCName);
                    value = (long) sum.getValue();
                    break;
                case Avg:
                    Avg avg = idBucket.getAggregations().get(valueCName);
                    value = (long) avg.getValue();
                    break;
                default:
                    avg = idBucket.getAggregations().get(valueCName);
                    value = (long) avg.getValue();
                    break;
            }

            KVInt kvInt = new KVInt();
            kvInt.setId(idBucket.getKeyAsString());
            kvInt.setValue(value);
            intValues.getValues().add(kvInt);
        }
        return intValues;
    }

    protected void functionAggregation(Function function, TermsAggregationBuilder parentAggBuilder, String valueCName) {
        switch (function) {
            case Avg:
                parentAggBuilder.subAggregation(AggregationBuilders.avg(valueCName).field(valueCName));
                break;
            case Sum:
                parentAggBuilder.subAggregation(AggregationBuilders.sum(valueCName).field(valueCName));
                break;
            default:
                parentAggBuilder.subAggregation(AggregationBuilders.avg(valueCName).field(valueCName));
                break;
        }
    }
}

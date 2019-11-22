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
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.query.sql.*;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class MetricsQueryEsDAO extends EsDAO implements IMetricsQueryDAO {

    public MetricsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public IntValues getValues(String indName, Downsampling downsampling, long startTB, long endTB, Where where, String valueCName,
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
                    value = (long)sum.getValue();
                    break;
                case Avg:
                    Avg avg = idBucket.getAggregations().get(valueCName);
                    value = (long)avg.getValue();
                    break;
                default:
                    avg = idBucket.getAggregations().get(valueCName);
                    value = (long)avg.getValue();
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

    @Override public IntValues getLinearIntValues(String indName, Downsampling downsampling, List<String> ids, String valueCName) throws IOException {
        String indexName = ModelName.build(downsampling, indName);

        SearchResponse response = getClient().ids(indexName, ids.toArray(new String[0]));
        Map<String, Map<String, Object>> idMap = toMap(response);

        IntValues intValues = new IntValues();
        for (String id : ids) {
            KVInt kvInt = new KVInt();
            kvInt.setId(id);
            kvInt.setValue(0);
            if (idMap.containsKey(id)) {
                Map<String, Object> source = idMap.get(id);
                kvInt.setValue(((Number)source.getOrDefault(valueCName, 0)).longValue());
            }
            intValues.getValues().add(kvInt);
        }

        return intValues;
    }

    @Override public Thermodynamic getThermodynamic(String indName, Downsampling downsampling, List<String> ids, String valueCName) throws IOException {
        String indexName = ModelName.build(downsampling, indName);

        Thermodynamic thermodynamic = new Thermodynamic();
        List<List<Long>> thermodynamicValueMatrix = new ArrayList<>();

        SearchResponse response = getClient().ids(indexName, ids.toArray(new String[0]));
        Map<String, Map<String, Object>> idMap = toMap(response);

        int numOfSteps = 0;
        for (String id : ids) {
            Map<String, Object> source = idMap.get(id);
            if (source == null) {
                // add empty list to represent no data exist for this time bucket
                thermodynamicValueMatrix.add(new ArrayList<>());
            } else {
                int axisYStep = ((Number)source.get(ThermodynamicMetrics.STEP)).intValue();
                thermodynamic.setAxisYStep(axisYStep);
                numOfSteps = ((Number)source.get(ThermodynamicMetrics.NUM_OF_STEPS)).intValue() + 1;

                String value = (String)source.get(ThermodynamicMetrics.DETAIL_GROUP);
                IntKeyLongValueHashMap intKeyLongValues = new IntKeyLongValueHashMap(5);
                intKeyLongValues.toObject(value);

                List<Long> axisYValues = new ArrayList<>();
                for (int i = 0; i < numOfSteps; i++) {
                    axisYValues.add(0L);
                }

                for (IntKeyLongValue intKeyLongValue : intKeyLongValues.values()) {
                    axisYValues.set(intKeyLongValue.getKey(), intKeyLongValue.getValue());
                }

                thermodynamicValueMatrix.add(axisYValues);
            }
        }

        thermodynamic.fromMatrixData(thermodynamicValueMatrix, numOfSteps);

        return thermodynamic;
    }

    private Map<String, Map<String, Object>> toMap(SearchResponse response) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        SearchHit[] hits = response.getHits().getHits();
        for (SearchHit hit : hits) {
            result.put(hit.getId(), hit.getSourceAsMap());
        }
        return result;
    }
}

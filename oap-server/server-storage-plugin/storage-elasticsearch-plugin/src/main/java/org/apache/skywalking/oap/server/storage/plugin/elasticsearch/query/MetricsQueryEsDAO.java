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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.HistogramMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class MetricsQueryEsDAO extends EsDAO implements IMetricsQueryDAO {

    public MetricsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public long readMetricsValue(final MetricsCondition condition,
                                final String valueColumnName,
                                final Duration duration) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        buildQuery(sourceBuilder, condition, duration);
        int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration).getValues().latestValue(defaultValue);
        }

        TermsAggregationBuilder entityIdAggregation = AggregationBuilders.terms(Metrics.ENTITY_ID)
                                                                         .field(Metrics.ENTITY_ID)
                                                                         .size(1);
        functionAggregation(function, entityIdAggregation, valueColumnName);

        sourceBuilder.aggregation(entityIdAggregation);

        SearchResponse response = getClient().search(condition.getName(), sourceBuilder);

        Terms idTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket idBucket : idTerms.getBuckets()) {
            switch (function) {
                case Sum:
                    Sum sum = idBucket.getAggregations().get(valueColumnName);
                    return (long) sum.getValue();
                case Avg:
                    Avg avg = idBucket.getAggregations().get(valueColumnName);
                    return (long) avg.getValue();
                default:
                    avg = idBucket.getAggregations().get(valueColumnName);
                    return (long) avg.getValue();
            }
        }
        return defaultValue;
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        SearchResponse response = getClient().ids(condition.getName(), ids.toArray(new String[0]));
        Map<String, Map<String, Object>> idMap = toMap(response);

        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        IntValues intValues = metricsValues.getValues();
        for (String id : ids) {
            KVInt kvInt = new KVInt();
            kvInt.setId(id);
            kvInt.setValue(0);
            if (idMap.containsKey(id)) {
                Map<String, Object> source = idMap.get(id);
                kvInt.setValue(((Number) source.getOrDefault(valueColumnName, 0)).longValue());
            } else {
                kvInt.setValue(ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
            }
            intValues.addKVInt(kvInt);
        }

        metricsValues.setValues(
            Util.sortValues(intValues, ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );

        return metricsValues;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<String> labels,
                                                        final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        SearchResponse response = getClient().ids(condition.getName(), ids.toArray(new String[0]));
        Map<String, DataTable> idMap = new HashMap<>();
        SearchHit[] hits = response.getHits().getHits();
        for (SearchHit hit : hits) {
            idMap.put(hit.getId(), new DataTable((String) hit.getSourceAsMap().getOrDefault(valueColumnName, "")));
        }
        return Util.composeLabelValue(condition, labels, ids, idMap);
    }

    @Override
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        SearchResponse response = getClient().ids(condition.getName(), ids.toArray(new String[0]));
        Map<String, Map<String, Object>> idMap = toMap(response);

        HeatMap heatMap = new HeatMap();

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        for (String id : ids) {
            Map<String, Object> source = idMap.get(id);
            if (source != null) {
                String value = (String) source.get(HistogramMetrics.DATASET);
                heatMap.buildColumn(id, value, defaultValue);
            }
        }

        heatMap.fixMissingColumns(ids, defaultValue);

        return heatMap;
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

    protected final void buildQuery(SearchSourceBuilder sourceBuilder, MetricsCondition condition, Duration duration) {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(Metrics.TIME_BUCKET)
                                                           .gte(duration.getStartTimeBucket())
                                                           .lte(duration.getEndTimeBucket());

        final String entityId = condition.getEntity().buildId();

        if (entityId == null) {
            sourceBuilder.query(rangeQueryBuilder);
        } else {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must().add(rangeQueryBuilder);
            boolQuery.must().add(QueryBuilders.termsQuery(Metrics.ENTITY_ID, entityId));

            sourceBuilder.query(boolQuery);
        }
        sourceBuilder.size(0);
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

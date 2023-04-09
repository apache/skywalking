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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.RangeQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHits;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.NullableValue;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils;

public class MetricsQueryEsDAO extends EsDAO implements IMetricsQueryDAO {

    public MetricsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public NullableValue readMetricsValue(final MetricsCondition condition,
                                          final String valueColumnName,
                                          final Duration duration) {
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final SearchBuilder sourceBuilder = buildQuery(condition, duration);
        int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, realValueColumn, duration)
                .getValues().latestValue(defaultValue);
        }

        final TermsAggregationBuilder entityIdAggregation =
            Aggregation.terms(Metrics.ENTITY_ID)
                       .field(Metrics.ENTITY_ID)
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                       .size(1);
        functionAggregation(function, entityIdAggregation, realValueColumn);

        sourceBuilder.aggregation(entityIdAggregation);

        final SearchResponse response = getClient().search(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName()),
            duration.getStartTimeBucketInSec(),
            duration.getEndTimeBucketInSec()), sourceBuilder.build());

        if (Objects.nonNull(response.getAggregations())) {
            final Map<String, Object> idTerms =
                (Map<String, Object>) response.getAggregations().get(Metrics.ENTITY_ID);
            final List<Map<String, Object>> buckets =
                (List<Map<String, Object>>) idTerms.get("buckets");

            for (Map<String, Object> idBucket : buckets) {
                final Map<String, Object> agg = (Map<String, Object>) idBucket.get(realValueColumn);
                return new NullableValue(((Number) agg.get("value")).longValue(), false);
            }
        }
        return new NullableValue(defaultValue, true);
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) {
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        String tableName =
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        Map<String, List<String>> indexIdsGroup = new HashMap<>();

        final List<String> ids = pointOfTimes.stream().map(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            if (IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
                id = IndexController.INSTANCE.generateDocId(condition.getName(), id);
            }
            String indexName = TimeSeriesUtils.queryIndexName(
                tableName, pointOfTime.getPoint(), duration.getStep(), false, false);
            indexIdsGroup.computeIfAbsent(indexName, v -> new ArrayList<>()).add(id);
            return id;
        }).collect(Collectors.toList());

        MetricsValues metricsValues = new MetricsValues();
        Optional<Documents> response = getClient().ids(indexIdsGroup);
        if (response.isPresent()) {
            Map<String, Map<String, Object>> idMap = toMap(response.get());

            // Label is null, because in readMetricsValues, no label parameter.
            IntValues intValues = metricsValues.getValues();
            for (String id : ids) {
                KVInt kvInt = new KVInt();
                kvInt.setId(id);
                if (idMap.containsKey(id)) {
                    Map<String, Object> source = idMap.get(id);
                    if (source.get(realValueColumn) != null) {
                        kvInt.setValue(((Number) source.get(realValueColumn)).longValue());
                    } else {
                        kvInt.setValue(ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
                        kvInt.setEmptyValue(true);
                    }
                    intValues.addKVInt(kvInt);
                }
            }
        }
        metricsValues.setValues(
            Util.sortValues(
                metricsValues.getValues(), ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );

        return metricsValues;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<String> labels,
                                                        final Duration duration) {
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        String tableName =
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());
        Map<String, List<String>> indexIdsGroup = new HashMap<>();

        boolean aggregationMode = !tableName.equals(condition.getName());
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            if (aggregationMode) {
                id = IndexController.INSTANCE.generateDocId(condition.getName(), id);
            }
            ids.add(id);
            String indexName = TimeSeriesUtils.queryIndexName(
                tableName, pointOfTime.getPoint(), duration.getStep(), false, false);
            indexIdsGroup.computeIfAbsent(indexName, v -> new ArrayList<>()).add(id);
        });

        Optional<Documents> response = getClient().ids(indexIdsGroup);
        Map<String, DataTable> idMap = new HashMap<>();

        if (response.isPresent()) {
            for (final Document document : response.get()) {
                if (document.getSource().get(realValueColumn) != null) {
                    idMap.put(
                        document.getId(),
                        new DataTable((String) document.getSource().get(realValueColumn))
                    );
                }
            }
        }
        return Util.sortValues(
            Util.composeLabelValue(condition, labels, idMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    @Override
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) {
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        String tableName =
            IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());
        Map<String, List<String>> indexIdsGroup = new HashMap<>();

        boolean aggregationMode = !tableName.equals(condition.getName());
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            if (aggregationMode) {
                id = IndexController.INSTANCE.generateDocId(condition.getName(), id);
            }
            ids.add(id);
            String indexName = TimeSeriesUtils.queryIndexName(
                tableName, pointOfTime.getPoint(), duration.getStep(), false, false);
            indexIdsGroup.computeIfAbsent(indexName, v -> new ArrayList<>()).add(id);
        });

        HeatMap heatMap = new HeatMap();

        Optional<Documents> response = getClient().ids(indexIdsGroup);
        if (!response.isPresent()) {
            return heatMap;
        }
        Map<String, Map<String, Object>> idMap = toMap(response.get());

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        for (String id : ids) {
            Map<String, Object> source = idMap.get(id);
            if (source != null) {
                String value = (String) source.get(realValueColumn);
                heatMap.buildColumn(id, value, defaultValue);
            }
        }

        heatMap.fixMissingColumns(ids, defaultValue);

        return heatMap;
    }

    protected void functionAggregation(Function function,
                                       TermsAggregationBuilder parentAggBuilder,
                                       String valueCName) {
        switch (function) {
            case Avg:
                parentAggBuilder.subAggregation(Aggregation.avg(valueCName).field(valueCName));
                break;
            case Sum:
                parentAggBuilder.subAggregation(Aggregation.sum(valueCName).field(valueCName));
                break;
            default:
                parentAggBuilder.subAggregation(Aggregation.avg(valueCName).field(valueCName));
                break;
        }
    }

    protected final SearchBuilder buildQuery(MetricsCondition condition, Duration duration) {
        final SearchBuilder sourceBuilder = Search.builder();

        final RangeQueryBuilder rangeQueryBuilder =
            Query.range(Metrics.TIME_BUCKET)
                 .gte(duration.getStartTimeBucket())
                 .lte(duration.getEndTimeBucket());

        final String entityId = condition.getEntity().buildId();

        if (entityId == null &&
            IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
            sourceBuilder.query(
                Query.bool()
                     .must(rangeQueryBuilder)
                     .must(Query.term(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ))
            );
        } else if (entityId == null) {
            sourceBuilder.query(rangeQueryBuilder);
        } else if (IndexController.LogicIndicesRegister.isMergedTable(condition.getName())) {
            sourceBuilder.query(
                Query.bool()
                     .must(rangeQueryBuilder)
                     .must(Query.terms(Metrics.ENTITY_ID, entityId))
                     .must(Query.term(
                         IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                         condition.getName()
                     ))
            );
        } else {
            sourceBuilder.query(
                Query.bool()
                     .must(rangeQueryBuilder)
                     .must(Query.terms(Metrics.ENTITY_ID, entityId))
            );
        }
        sourceBuilder.size(0);

        return sourceBuilder;
    }

    private Map<String, Map<String, Object>> toMap(SearchHits hits) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (final SearchHit hit : hits) {
            result.put(hit.getId(), hit.getSource());
        }
        return result;
    }

    private Map<String, Map<String, Object>> toMap(Documents documents) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (final Document document : documents) {
            result.put(document.getId(), document.getSource());
        }
        return result;
    }
}

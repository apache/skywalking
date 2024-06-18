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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.RangeQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
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
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
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
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) {
        DebuggingTraceContext traceContext = DebuggingTrace.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: readMetricsValues");
                span.setMsg(
                    "MetricsCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Duration: " + duration);
            }
            return invokeReadMetricsValues(condition, valueColumnName, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<KeyValue> labels,
                                                        final Duration duration) {
        DebuggingTraceContext traceContext = DebuggingTrace.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: readLabeledMetricsValues");
                span.setMsg(
                    "MetricsCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Labels: " + labels + ", Duration: " + duration);
            }
            return invokeReadLabeledMetricsValues(condition, valueColumnName, labels, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    private MetricsValues invokeReadMetricsValues(final MetricsCondition condition,
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
        Optional<Documents> response = traceQueryResponse(indexIdsGroup);
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

    private List<MetricsValues> invokeReadLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<KeyValue> labels,
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

        Optional<Documents> response = traceQueryResponse(indexIdsGroup);
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
            Util.composeLabelValue(condition.getName(), labels, ids, idMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    private Optional<Documents> traceQueryResponse(Map<String, List<String>> indexIdsGroup) {
        DebuggingTraceContext traceContext = DebuggingTrace.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Elasticsearch");
                builder.append("Condition: ").append("indices: ").append(indexIdsGroup.keySet());
                span.setMsg(builder.toString());
            }
            Optional<Documents> response = getClient().ids(indexIdsGroup);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(response.isPresent() ? new Gson().toJson(response.get()) : "null");
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    public List<MetricsValues> readLabeledMetricsValuesWithoutEntity(final String metricName,
                                                    final String valueColumnName,
                                                    final List<KeyValue> labels,
                                                    final Duration duration) {
        final SearchBuilder search = Search.builder().size(METRICS_VALUES_WITHOUT_ENTITY_LIMIT);
        final BoolQueryBuilder query = Query.bool().must(Query.range(Metrics.TIME_BUCKET)
                                                              .lte(duration.getEndTimeBucket())
                                                              .gte(duration.getStartTimeBucket()));
        if (IndexController.LogicIndicesRegister.isMergedTable(metricName)) {
                query.must(Query.term(
                IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                metricName
            ));
            search.query(query);
        }
        final SearchResponse response = getClient().search(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(metricName),
            duration.getStartTimeBucketInSec(),
            duration.getEndTimeBucketInSec()), search.build());
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(metricName, valueColumnName);
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        Map<String, DataTable> idMap = new HashMap<>();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        for (SearchHit searchHit : response.getHits()) {
            if (searchHit.getSource().get(realValueColumn) != null) {
                idMap.put(
                    searchHit.getId(),
                    new DataTable((String) searchHit.getSource().get(realValueColumn))
                );
                ids.add(searchHit.getId());
            }
        }
        return Util.sortValues(
            Util.composeLabelValue(metricName, labels, ids, idMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(metricName)
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

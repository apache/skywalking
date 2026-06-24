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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.RangeQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
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
import org.apache.skywalking.oap.server.core.storage.annotation.InspectQueryContext;
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
                                           final Duration duration) throws IOException {
        // getPhysicalTableName / isMergedTable are overlay-aware: a foreign metric (admin inspect value
        // path) resolves to the merged METRICS_LOGIC_TABLE_NAME index and reports mergedTable=true with
        // no explicit branch here. The one case the overlay cannot cover is logicSharding=true, where the
        // physical index derives from the metric's (absent) stream class — reject that up front.
        if (InspectQueryContext.get(condition.getName()) != null && IndexController.INSTANCE.isLogicSharding()) {
            throw new IOException(
                "inspecting a foreign metric is unsupported under ES logicSharding=true: the physical "
                    + "index is derived from the metric's stream class, which this OAP does not have for "
                    + condition.getName());
        }
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final boolean mergedTable = IndexController.LogicIndicesRegister.isMergedTable(condition.getName());
        final String tableName = IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        Map<String, List<String>> indexIdsGroup = new HashMap<>();

        final List<String> ids = pointOfTimes.stream().map(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            if (mergedTable) {
                id = IndexController.INSTANCE.generateDocId(condition.getName(), id);
            }
            String indexName = TimeSeriesUtils.queryIndexName(
                tableName, pointOfTime.getPoint(), duration.getStep(), false, false);
            indexIdsGroup.computeIfAbsent(indexName, v -> new ArrayList<>()).add(id);
            return id;
        }).collect(Collectors.toList());

        MetricsValues metricsValues = new MetricsValues();
        Optional<Documents> response = idsDebuggable(indexIdsGroup);
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
                                                        final List<KeyValue> labels,
                                                        final Duration duration) throws IOException {
        // getPhysicalTableName is overlay-aware: a foreign metric (admin inspect value path) resolves
        // to the merged METRICS_LOGIC_TABLE_NAME index, so aggregationMode derives true and the doc-id
        // is metric-name-prefixed without an explicit foreign branch. logicSharding cannot be resolved
        // for a foreign metric (index derives from the absent stream class) — reject it up front.
        if (InspectQueryContext.get(condition.getName()) != null && IndexController.INSTANCE.isLogicSharding()) {
            throw new IOException(
                "inspecting a foreign metric is unsupported under ES logicSharding=true: the physical "
                    + "index is derived from the metric's stream class, which this OAP does not have for "
                    + condition.getName());
        }
        final String realValueColumn = IndexController.LogicIndicesRegister.getPhysicalColumnName(condition.getName(), valueColumnName);
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final String tableName = IndexController.LogicIndicesRegister.getPhysicalTableName(condition.getName());
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

        Optional<Documents> response = idsDebuggable(indexIdsGroup);
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
    public List<String> listEntityIdsInRange(final String metricName,
                                             final String valueColumnName,
                                             final String valueType,
                                             final Duration duration,
                                             final int limit) throws IOException {
        // valueType != null signals a foreign metric (not defined on this OAP). The value column
        // is unused by ES entity enumeration; only the physical index + discriminator differ.
        final boolean foreign = valueType != null;
        final String physicalIndex;
        final boolean filterByDiscriminator;
        if (foreign) {
            // Resolve from running config, not the local registry. In the default merged mode
            // every metric lives in the single METRICS_LOGIC_TABLE_NAME index and carries the
            // metric_table discriminator. Under logicSharding the physical index is derived from
            // the (absent) stream class, so it cannot be resolved without the local model.
            if (IndexController.INSTANCE.isLogicSharding()) {
                throw new IOException(
                    "inspecting a foreign metric is unsupported under ES logicSharding=true: the "
                        + "physical index is derived from the metric's stream class, which this OAP "
                        + "does not have for " + metricName);
            }
            physicalIndex = IndexController.METRICS_LOGIC_TABLE_NAME;
            filterByDiscriminator = true;
        } else {
            physicalIndex = IndexController.LogicIndicesRegister.getPhysicalTableName(metricName);
            filterByDiscriminator = IndexController.LogicIndicesRegister.isMergedTable(metricName);
        }
        final SearchBuilder search = Search.builder().size(limit);
        // Most-recent-first ordering must be explicit — without sort the hit set is
        // score / index-internal ordered, so a hot entity that ingested late can be dropped
        // before the limit is reached.
        search.sort(Metrics.TIME_BUCKET, Sort.Order.DESC);
        final BoolQueryBuilder query = Query.bool().must(Query.range(Metrics.TIME_BUCKET)
                                                              .lte(duration.getEndTimeBucket())
                                                              .gte(duration.getStartTimeBucket()));
        if (filterByDiscriminator) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                metricName
            ));
        }
        search.query(query);
        final SearchResponse response = getClient().search(new TimeRangeIndexNameGenerator(
            physicalIndex,
            duration.getStartTimeBucketInSec(),
            duration.getEndTimeBucketInSec()), search.build());
        // Top-N hits across the time range, dedup client-side on entity_id. LinkedHashSet
        // preserves the time_bucket-DESC ordering ES returned.
        final LinkedHashSet<String> entityIds = new LinkedHashSet<>();
        for (final SearchHit searchHit : response.getHits()) {
            final Object eid = searchHit.getSource().get(Metrics.ENTITY_ID);
            if (eid instanceof String) {
                entityIds.add((String) eid);
            }
        }
        return new ArrayList<>(entityIds);
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

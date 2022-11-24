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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.joda.time.DateTime;

@Slf4j
public class MetricsEsDAO extends EsDAO implements IMetricsDAO {
    protected final StorageBuilder<Metrics> storageBuilder;

    public MetricsEsDAO(ElasticSearchClient client,
                           StorageBuilder<Metrics> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) {
        Map<String, List<Metrics>> groupIndices = new HashMap<>();
        List<Metrics> result = new ArrayList<>(metrics.size());

        if (model.isTimeRelativeID()) {
            metrics.forEach(metric -> {
                // Try to use with timestamp index name(write index),
                String indexName = TimeSeriesUtils.writeIndexName(model, metric.getTimeBucket());
                groupIndices.computeIfAbsent(indexName, v -> new ArrayList<>()).add(metric);
            });

            Map<String, List<String>> indexIdsGroup = new HashMap<>();
            groupIndices.forEach((tableName, metricList) -> {
                List<String> ids = metricList.stream()
                                             .map(item -> IndexController.INSTANCE.generateDocId(model, item.id()))
                                             .collect(Collectors.toList());
                indexIdsGroup.put(tableName, ids);
            });
            if (!indexIdsGroup.isEmpty()) {
                final Optional<Documents> response = getClient().ids(indexIdsGroup);
                response.ifPresent(documents -> documents.forEach(document -> {
                    Metrics source = storageBuilder.storage2Entity(new ElasticSearchConverter.ToEntity(model.getName(), document.getSource()));
                    result.add(source);
                }));
            }
        } else {
            metrics.forEach(metric -> {
                // Metadata level metrics, always use alias name, due to the physical index of the records
                // can't be located through timestamp.
                String indexName = IndexController.INSTANCE.getTableName(model);
                groupIndices.computeIfAbsent(indexName, v -> new ArrayList<>()).add(metric);
            });
            groupIndices.forEach((tableName, metricList) -> {
                List<String> ids = metricList.stream()
                                             .map(item -> IndexController.INSTANCE.generateDocId(model, item.id()))
                                             .collect(Collectors.toList());
                final SearchResponse response = getClient().searchIDs(tableName, ids);
                response.getHits().getHits().forEach(hit -> {
                    Metrics source = storageBuilder.storage2Entity(new ElasticSearchConverter.ToEntity(model.getName(), hit.getSource()));
                    result.add(source);
                });
            });
        }
        return result;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) {
        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(model.getName());
        storageBuilder.entity2Storage(metrics, toStorage);
        Map<String, Object> builder = IndexController.INSTANCE.appendTableColumn(model, toStorage.obtain());
        String modelName = TimeSeriesUtils.writeIndexName(model, metrics.getTimeBucket());
        String id = IndexController.INSTANCE.generateDocId(model, metrics.id());
        return new MetricIndexRequestWrapper(getClient().prepareInsert(modelName, id, builder), callback);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) {
        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(model.getName());
        storageBuilder.entity2Storage(metrics, toStorage);
        Map<String, Object> builder =
            IndexController.INSTANCE.appendTableColumn(model, toStorage.obtain());
        String modelName = TimeSeriesUtils.writeIndexName(model, metrics.getTimeBucket());
        String id = IndexController.INSTANCE.generateDocId(model, metrics.id());
        return new MetricIndexUpdateWrapper(getClient().prepareUpdate(modelName, id, builder), callback);
    }

    @Override
    public boolean isExpiredCache(final Model model,
                                  final Metrics cachedValue,
                                  final long currentTimeMillis,
                                  final int ttl) {
        final long metricTimestamp = TimeBucket.getTimestamp(
            cachedValue.getTimeBucket(), model.getDownsampling());
        // Fast fail check. If the duration is still less than TTL - 1 days(absolute)
        // the cache should not be expired.
        if (currentTimeMillis - metricTimestamp < TimeUnit.DAYS.toMillis(ttl - 1)) {
            return false;
        }
        final long deadline = Long.parseLong(new DateTime(currentTimeMillis).plusDays(-ttl).toString("yyyyMMdd"));
        final long timeBucket = TimeBucket.getTimeBucket(metricTimestamp, DownSampling.Day);
        // If time bucket is earlier or equals(mostly) the deadline, then the cached metric is expired.
        if (timeBucket <= deadline) {
            return true;
        }
        return false;
    }
}

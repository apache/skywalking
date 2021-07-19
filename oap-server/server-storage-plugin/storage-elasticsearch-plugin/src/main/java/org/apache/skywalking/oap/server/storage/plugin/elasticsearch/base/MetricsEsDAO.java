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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.IndicesMetadataCache;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.joda.time.DateTime;

import static java.util.stream.Collectors.groupingBy;

@Slf4j
public class MetricsEsDAO extends EsDAO implements IMetricsDAO {
    protected final StorageHashMapBuilder<Metrics> storageBuilder;

    protected MetricsEsDAO(ElasticSearchClient client,
                           StorageHashMapBuilder<Metrics> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        Map<String, List<Metrics>> groupIndices
            = metrics.stream()
                     .collect(
                         groupingBy(metric -> {
                             if (model.isTimeRelativeID()) {
                                 // Try to use with timestamp index name(write index),
                                 // if latest cache shows this name doesn't exist,
                                 // then fail back to template alias name.
                                 // This should only happen in very rare case, such as this is the time to create new index
                                 // as a new day comes, and the index cache is  pseudo real time.
                                 // This case doesn't affect the result, just has lower performance due to using the alias name.
                                 // Another case is that a removed index showing existing also due to latency,
                                 // which could cause multiGet fails
                                 // but this should not happen in the real runtime, TTL timer only removed the oldest indices,
                                 // which should not have an update/insert.
                                 String indexName = TimeSeriesUtils.writeIndexName(model, metric.getTimeBucket());
                                 // Format the name to follow the global physical index naming policy.
                                 if (!IndicesMetadataCache.INSTANCE.isExisting(
                                     getClient().formatIndexName(indexName))) {
                                     indexName = IndexController.INSTANCE.getTableName(model);
                                 }
                                 return indexName;
                             } else {
                                 // Metadata level metrics, always use alias name, due to the physical index of the records
                                 // can't be located through timestamp.
                                 return IndexController.INSTANCE.getTableName(model);
                             }
                         })
                     );

        // The groupIndices mostly include one or two group,
        // the current day and the T-1 day(if at the edge between days)
        List<Metrics> result = new ArrayList<>(metrics.size());
        groupIndices.forEach((tableName, metricList) -> {
            String[] ids = metricList.stream()
                                     .map(item -> IndexController.INSTANCE.generateDocId(model, item.id()))
                                     .toArray(String[]::new);
            try {
                SearchResponse response = getClient().ids(tableName, ids);
                for (int i = 0; i < response.getHits().getHits().length; i++) {
                    Metrics source = storageBuilder.storage2Entity(response.getHits().getAt(i).getSourceAsMap());
                    result.add(source);
                }
            } catch (IOException e) {
                log.error("multiGet id=" + Arrays.toString(ids) + " from " + tableName + " fails.", e);
            }
        });

        return result;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(
            IndexController.INSTANCE.appendMetricTableColumn(model, storageBuilder.entity2Storage(metrics)));
        String modelName = TimeSeriesUtils.writeIndexName(model, metrics.getTimeBucket());
        String id = IndexController.INSTANCE.generateDocId(model, metrics.id());
        return getClient().prepareInsert(modelName, id, builder);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(
            IndexController.INSTANCE.appendMetricTableColumn(model, storageBuilder.entity2Storage(metrics)));
        String modelName = TimeSeriesUtils.writeIndexName(model, metrics.getTimeBucket());
        String id = IndexController.INSTANCE.generateDocId(model, metrics.id());
        return getClient().prepareUpdate(modelName, id, builder);
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
        final long timeBucket = TimeBucket.getTimeBucket(cachedValue.getTimeBucket(), DownSampling.Day);
        // If time bucket is earlier or equals(mostly) the deadline, then the cached metric is expired.
        if (timeBucket <= deadline) {
            return true;
        }
        return false;
    }
}

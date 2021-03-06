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
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class MetricsEsDAO extends EsDAO implements IMetricsDAO {
    protected final StorageMode storageMode;
    protected final StorageHashMapBuilder<Metrics> storageBuilder;

    protected MetricsEsDAO(ElasticSearchClient client,
                           StorageHashMapBuilder<Metrics> storageBuilder,
                           StorageMode storageMode) {
        super(client);
        this.storageBuilder = storageBuilder;
        this.storageMode = storageMode;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        String tableName = storageMode.getTableName(model);
        String[] ids = metrics.stream()
                              .map(item -> storageMode.generateDocId(model, item.id()))
                              .toArray(String[]::new);
        SearchResponse response = getClient().ids(tableName, ids);
        List<Metrics> result = new ArrayList<>(response.getHits().getHits().length);
        for (int i = 0; i < response.getHits().getHits().length; i++) {
            Metrics source = storageBuilder.storage2Entity(response.getHits().getAt(i).getSourceAsMap());
            result.add(source);
        }
        return result;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(
            storageMode.appendAggregationColumn(model, storageBuilder.entity2Storage(metrics)));
        String modelName = TimeSeriesUtils.writeIndexName(model, storageMode, metrics.getTimeBucket());
        return getClient().prepareInsert(modelName, storageMode.generateDocId(model, metrics.id()), builder);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(
            storageMode.appendAggregationColumn(model, storageBuilder.entity2Storage(metrics)));
        String modelName = TimeSeriesUtils.writeIndexName(model, storageMode, metrics.getTimeBucket());
        return getClient().prepareUpdate(modelName, storageMode.generateDocId(model, metrics.id()), builder);
    }
}

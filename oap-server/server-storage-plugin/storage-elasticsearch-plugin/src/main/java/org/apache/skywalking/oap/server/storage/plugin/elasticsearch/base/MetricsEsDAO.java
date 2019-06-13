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
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * @author peng-yongsheng
 */
public class MetricsEsDAO extends EsDAO implements IMetricsDAO<IndexRequest, UpdateRequest> {

    private final StorageBuilder<Metrics> storageBuilder;

    MetricsEsDAO(ElasticSearchClient client, StorageBuilder<Metrics> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override public Metrics get(Model model, Metrics metrics) throws IOException {
        String modelName = TimeSeriesUtils.timeSeries(model, metrics.getTimeBucket());
        GetResponse response = getClient().get(modelName, metrics.id());
        if (response.isExists()) {
            return storageBuilder.map2Data(response.getSource());
        } else {
            return null;
        }
    }

    @Override public IndexRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(storageBuilder.data2Map(metrics));
        String modelName = TimeSeriesUtils.timeSeries(model, metrics.getTimeBucket());
        return getClient().prepareInsert(modelName, metrics.id(), builder);
    }

    @Override public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        XContentBuilder builder = map2builder(storageBuilder.data2Map(metrics));
        String modelName = TimeSeriesUtils.timeSeries(model, metrics.getTimeBucket());
        return getClient().prepareUpdate(modelName, metrics.id(), builder);
    }
}

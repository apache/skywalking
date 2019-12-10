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
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.*;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * @author peng-yongsheng
 */
public class MetricsEsDAO extends EsDAO implements IMetricsDAO {

    protected final StorageBuilder<Metrics> storageBuilder;

    protected MetricsEsDAO(ElasticSearchClient client, StorageBuilder<Metrics> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override public List<Metrics> multiGet(Model model, List<String> ids) throws IOException {
        SearchResponse response = getClient().ids(model.getName(), ids.toArray(new String[0]));

        List<Metrics> result = new ArrayList<>((int)response.getHits().totalHits);
        for (int i = 0; i < response.getHits().totalHits; i++) {
            Metrics source = storageBuilder.map2Data(response.getHits().getAt(i).getSourceAsMap());
            result.add(source);
        }
        return result;
    }

    @Override public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
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

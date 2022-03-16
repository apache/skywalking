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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
@RequiredArgsConstructor
public class IoTDBMetricsDAO implements IMetricsDAO {
    private final IoTDBClient client;
    private final StorageBuilder<Metrics> storageBuilder;

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        for (Metrics metric : metrics) {
            query.append(", ");
            query = client.addModelPath(query, model.getName());
            query.append(IoTDBClient.DOT).append(client.indexValue2LayerName(metric.id()));
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);
        String queryString = query.toString().replaceFirst(", ", "");
        List<? super StorageData> storageDataList = client.filterQuery(model.getName(), queryString, storageBuilder);
        List<Metrics> newMetrics = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> newMetrics.add((Metrics) storageData));
        return newMetrics;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) {
        final long timestamp = TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling());
        return new IoTDBInsertRequest(model.getName(), timestamp, metrics, storageBuilder);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) {
        return (UpdateRequest) prepareBatchInsert(model, metrics);
    }
}

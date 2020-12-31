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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

public class H2MetricsDAO extends H2SQLExecutor implements IMetricsDAO {

    private JDBCHikariCPClient h2Client;
    private StorageBuilder<Metrics> storageBuilder;

    public H2MetricsDAO(JDBCHikariCPClient h2Client, StorageBuilder<Metrics> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        String[] ids = metrics.stream().map(Metrics::id).collect(Collectors.toList()).toArray(new String[] {});
        List<StorageData> storageDataList = getByIDs(h2Client, model.getName(), ids, storageBuilder);
        List<Metrics> result = new ArrayList<>(storageDataList.size());
        for (StorageData storageData : storageDataList) {
            result.add((Metrics) storageData);
        }
        return result;
    }

    @Override
    public SQLExecutor prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        return getInsertExecutor(model.getName(), metrics, storageBuilder);
    }

    @Override
    public SQLExecutor prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        return getUpdateExecutor(model.getName(), metrics, storageBuilder);
    }
}

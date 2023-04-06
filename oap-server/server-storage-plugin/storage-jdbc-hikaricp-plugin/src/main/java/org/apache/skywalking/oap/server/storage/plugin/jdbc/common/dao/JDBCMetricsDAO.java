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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCMetricsDAO extends JDBCSQLExecutor implements IMetricsDAO {
    private final JDBCClient jdbcClient;
    private final StorageBuilder<Metrics> storageBuilder;

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws Exception {
        final var ids = metrics.stream().map(m -> TableHelper.generateId(model, m.id().build())).collect(toList());
        final var storageDataList = getByIDs(jdbcClient, model.getName(), ids, storageBuilder);
        final var result = new ArrayList<Metrics>(storageDataList.size());
        for (StorageData storageData : storageDataList) {
            result.add((Metrics) storageData);
        }
        return result;
    }

    @Override
    public SQLExecutor prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        return getInsertExecutor(model, metrics, metrics.getTimeBucket(), storageBuilder, new HashMapConverter.ToStorage(), callback);
    }

    @Override
    public SQLExecutor prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) {
        return getUpdateExecutor(model, metrics, metrics.getTimeBucket(), storageBuilder, callback);
    }
}

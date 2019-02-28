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
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.IIndicatorDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

/**
 * @author wusheng
 */
public class H2IndicatorDAO extends H2SQLExecutor implements IIndicatorDAO<SQLExecutor, SQLExecutor> {
    private JDBCHikariCPClient h2Client;
    private StorageBuilder<Indicator> storageBuilder;

    public H2IndicatorDAO(JDBCHikariCPClient h2Client, StorageBuilder<Indicator> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override public Indicator get(String modelName, Indicator indicator) throws IOException {
        return (Indicator)getByID(h2Client, modelName, indicator.id(), storageBuilder);
    }

    @Override public SQLExecutor prepareBatchInsert(String modelName, Indicator indicator) throws IOException {
        return getInsertExecutor(modelName, indicator, storageBuilder);
    }

    @Override public SQLExecutor prepareBatchUpdate(String modelName, Indicator indicator) throws IOException {
        return getUpdateExecutor(modelName, indicator, storageBuilder);
    }
}

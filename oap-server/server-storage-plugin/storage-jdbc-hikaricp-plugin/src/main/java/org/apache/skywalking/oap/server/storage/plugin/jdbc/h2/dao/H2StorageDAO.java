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

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

/**
 * @author wusheng
 */
public class H2StorageDAO implements StorageDAO<SQLExecutor, SQLExecutor> {

    private JDBCHikariCPClient h2Client;

    public H2StorageDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public IMetricsDAO<SQLExecutor, SQLExecutor> newMetricsDao(StorageBuilder<Metrics> storageBuilder) {
        return new H2MetricsDAO(h2Client, storageBuilder);
    }

    @Override public IRegisterDAO newRegisterDao(StorageBuilder<RegisterSource> storageBuilder) {
        return new H2RegisterDAO(h2Client, storageBuilder);
    }

    @Override public IRecordDAO newRecordDao(StorageBuilder<Record> storageBuilder) {
        return new H2RecordDAO(h2Client, storageBuilder);
    }
}

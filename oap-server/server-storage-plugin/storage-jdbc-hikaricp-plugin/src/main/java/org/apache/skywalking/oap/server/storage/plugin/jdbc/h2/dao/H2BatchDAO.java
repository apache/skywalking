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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.BatchSQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

@Slf4j
public class H2BatchDAO implements IBatchDAO {
    private JDBCHikariCPClient h2Client;
    private final DataCarrier<PrepareRequest> dataCarrier;
    private int maxBatchSqlSize = 2000;
    private int h2AsyncBatchPersistentPoolSize = 4;
    private int h2AsyncBatchPersistentChannelSize = 4;

    public H2BatchDAO(JDBCHikariCPClient h2Client, int batchSqlSize, int h2AsyncBatchPersistentPoolSize, int h2AsyncBatchPersistentChannelSize) {
        this.h2Client = h2Client;
        String name = "H2_ASYNCHRONOUS_BATCH_PERSISTENT";
        if (log.isDebugEnabled()) {
            log.debug("H2_ASYNCHRONOUS_BATCH_PERSISTENT poolSize: {},channelSize: {},maxBatchSqlSize:{}", h2AsyncBatchPersistentPoolSize, h2AsyncBatchPersistentChannelSize, batchSqlSize);
        }
        this.dataCarrier = new DataCarrier<>(name, h2AsyncBatchPersistentChannelSize, 10000);
        this.dataCarrier.consume(new H2BatchDAO.H2BatchConsumer(this), h2AsyncBatchPersistentPoolSize, 20);
    }

    @Override
    public void flush(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isEmpty(prepareRequests)) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("to execute sql statements execute, data size: {}, maxBatchSqlSize: {}", prepareRequests.size(), maxBatchSqlSize);
        }
        if (maxBatchSqlSize <= 1) {
            executeSql(prepareRequests);
        } else {
            executeSql(prepareRequests, maxBatchSqlSize);
        }
        if (log.isDebugEnabled()) {
            log.debug("execute sql statements done, data size: {}, maxBatchSqlSize: {}", prepareRequests.size(), maxBatchSqlSize);
        }
    }

    private void executeSql(List<PrepareRequest> prepareRequests) {
        if (log.isDebugEnabled()) {
            log.debug("execute sql one by one.data size: {}", prepareRequests.size());
        }
        try (Connection connection = h2Client.getConnection()) {
            for (PrepareRequest prepareRequest : prepareRequests) {
                try {
                    SQLExecutor sqlExecutor = (SQLExecutor) prepareRequest;
                    sqlExecutor.invoke(connection);
                } catch (SQLException e) {
                    // Just avoid one execution failure makes the rest of batch failure.
                    log.error(e.getMessage(), e);
                }
            }
        } catch (SQLException | JDBCClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void executeSql(List<PrepareRequest> prepareRequests, int maxBatchSqlSize) {
        if (log.isDebugEnabled()) {
            log.debug("execute sql batch. data size:{}", prepareRequests.size());
        }
        Map<String, List<PrepareRequest>> batchRequestMap = new HashMap<>();
        for (PrepareRequest prepareRequest : prepareRequests) {
            SQLExecutor sqlExecutor = (SQLExecutor) prepareRequest;
            if (batchRequestMap.get(sqlExecutor.getSql()) == null) {
                List<PrepareRequest> prepareRequestList = new ArrayList<>();
                batchRequestMap.put(sqlExecutor.getSql(), prepareRequestList);
            }
            batchRequestMap.get(sqlExecutor.getSql()).add(prepareRequest);
        }
        try (Connection connection = h2Client.getConnection()) {
            try {
                for (String key : batchRequestMap.keySet()) {
                    BatchSQLExecutor batchSQLExecutor = new BatchSQLExecutor(key, batchRequestMap.get(key));
                    batchSQLExecutor.invoke(connection, maxBatchSqlSize);
                }
            } catch (SQLException e) {
                // Just avoid one execution failure makes the rest of batch failure.
                log.error(e.getMessage(), e);
            }

        } catch (SQLException | JDBCClientException e) {
            log.warn("execute sql failed, discard data size: {}", prepareRequests.size());
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        this.dataCarrier.produce(insertRequest);
    }

    private class H2BatchConsumer implements IConsumer<PrepareRequest> {

        private final H2BatchDAO h2BatchDAO;

        private H2BatchConsumer(H2BatchDAO h2BatchDAO) {
            this.h2BatchDAO = h2BatchDAO;
        }

        @Override
        public void init(final Properties properties) {

        }

        @Override
        public void consume(List<PrepareRequest> prepareRequests) {
            h2BatchDAO.flush(prepareRequests);
        }

        @Override
        public void onError(List<PrepareRequest> prepareRequests, Throwable t) {
            log.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {
        }
    }
}

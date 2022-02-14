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

package org.apache.skywalking.oap.server.storage.plugin.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * A Batch SQL executor.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchSQLExecutor implements InsertRequest, UpdateRequest {

    private final List<PrepareRequest> prepareRequests;

    public void invoke(Connection connection, int maxBatchSqlSize) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("execute sql batch. sql by key size: {}", prepareRequests.size());
        }
        if (prepareRequests.size() == 0) {
            return;
        }
        String sql = prepareRequests.get(0).toString();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int pendingCount = 0;
            for (int k = 0; k < prepareRequests.size(); k++) {
                SQLExecutor sqlExecutor = (SQLExecutor) prepareRequests.get(k);
                sqlExecutor.setParameters(preparedStatement);
                preparedStatement.addBatch();
                if (k > 0 && k % maxBatchSqlSize == 0) {
                    executeBatch(preparedStatement, maxBatchSqlSize, sql);
                    pendingCount = 0;
                } else {
                    pendingCount++;
                }
            }
            if (pendingCount > 0) {
                executeBatch(preparedStatement, pendingCount, sql);
            }
        }
    }

    private void executeBatch(PreparedStatement preparedStatement, int pendingCount, String sql) throws SQLException {
        long start = System.currentTimeMillis();
        preparedStatement.executeBatch();
        if (log.isDebugEnabled()) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            log.debug("execute batch sql, batch size: {}, cost:{}ms, sql: {}", pendingCount, cost, sql);
        }
    }
}

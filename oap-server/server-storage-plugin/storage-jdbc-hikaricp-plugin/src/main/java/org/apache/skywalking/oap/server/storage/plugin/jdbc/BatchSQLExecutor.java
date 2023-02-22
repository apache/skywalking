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
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Batch SQL executor.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchSQLExecutor implements InsertRequest, UpdateRequest {
    private final JDBCClient jdbcClient;
    private final List<PrepareRequest> prepareRequests;

    public void invoke(int maxBatchSqlSize) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("execute sql batch. sql by key size: {}", prepareRequests.size());
        }
        if (prepareRequests.size() == 0) {
            return;
        }
        final var sql = prepareRequests.get(0).toString();
        final var bulkRequest = new ArrayList<PrepareRequest>(maxBatchSqlSize);
        try (final var connection = jdbcClient.getConnection();
             final var preparedStatement = connection.prepareStatement(sql)) {
            var pendingCount = 0;
            for (final var prepareRequest : prepareRequests) {
                final var sqlExecutor = (SQLExecutor) prepareRequest;
                sqlExecutor.setParameters(preparedStatement);
                preparedStatement.addBatch();
                bulkRequest.add(sqlExecutor);
                if (bulkRequest.size() == maxBatchSqlSize) {
                    executeBatch(preparedStatement, maxBatchSqlSize, sql, bulkRequest);
                    bulkRequest.clear();
                    pendingCount = 0;
                } else {
                    pendingCount++;
                }
            }
            if (pendingCount > 0) {
                executeBatch(preparedStatement, pendingCount, sql, bulkRequest);
                bulkRequest.clear();
            }
        }
    }

    private void executeBatch(PreparedStatement preparedStatement,
                              int pendingCount,
                              String sql,
                              List<PrepareRequest> bulkRequest) throws SQLException {
        final var start = System.currentTimeMillis();
        final var executeBatchResults = preparedStatement.executeBatch();
        final var isInsert = bulkRequest.get(0) instanceof InsertRequest;
        for (int i = 0; i < executeBatchResults.length; i++) {
            if (executeBatchResults[i] == 1 && isInsert) {
                // Insert successfully.
                ((InsertRequest) bulkRequest.get(i)).onInsertCompleted();
            } else if (executeBatchResults[i] == 0 && !isInsert) {
                // Update Failure.
                ((UpdateRequest) bulkRequest.get(i)).onUpdateFailure();
            }
        }
        if (log.isDebugEnabled()) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            log.debug("execute batch sql, batch size: {}, cost:{}ms, sql: {}", pendingCount, cost, sql);
        }
    }

    @Override
    public void onInsertCompleted() {
        throw new UnexpectedException("BatchSQLExecutor.onInsertCompleted should not be called");
    }

    @Override
    public void onUpdateFailure() {
        throw new UnexpectedException("BatchSQLExecutor.onUpdateFailure should not be called");
    }
}

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

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
        List<PrepareRequest> bulkRequest = new ArrayList<>(maxBatchSqlSize);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int pendingCount = 0;
            for (int k = 0; k < prepareRequests.size(); k++) {
                SQLExecutor sqlExecutor = (SQLExecutor) prepareRequests.get(k);
                sqlExecutor.setParameters(preparedStatement);
                preparedStatement.addBatch();
                bulkRequest.add(sqlExecutor);
                if (k > 0 && k % maxBatchSqlSize == 0) {
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
        long start = System.currentTimeMillis();
        final int[] executeBatchResults = preparedStatement.executeBatch();
        boolean isInsert = bulkRequest.get(0) instanceof InsertRequest;
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

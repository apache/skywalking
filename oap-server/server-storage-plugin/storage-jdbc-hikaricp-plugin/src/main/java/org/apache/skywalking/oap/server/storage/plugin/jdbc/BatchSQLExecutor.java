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
public class BatchSQLExecutor implements InsertRequest, UpdateRequest {

    private String sql;
    private List<PrepareRequest> prepareRequests;

    public BatchSQLExecutor(String sql, List<PrepareRequest> prepareRequests) {
        this.sql = sql;
        this.prepareRequests = prepareRequests;
    }

    public void invoke(Connection connection, int maxBatchSqlSize) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("execute sql batch.sql by key size: {},sql:{}", prepareRequests.size(), sql);
        }
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        int pendingCount = 0;
        for (int k = 0; k < prepareRequests.size(); k++) {
            SQLExecutor sqlExecutor = (SQLExecutor) prepareRequests.get(k);
            for (int i = 0; i < sqlExecutor.getParam().size(); i++) {
                preparedStatement.setObject(i + 1, sqlExecutor.getParam().get(i));
            }
            preparedStatement.addBatch();
            if (k > 0 && k % maxBatchSqlSize == 0) {
                long start = System.currentTimeMillis();
                preparedStatement.executeBatch();
                long end = System.currentTimeMillis();
                long cost = end - start;
                if (log.isDebugEnabled()) {
                    log.debug("execute batch sql,batch size: {}, cost:{},sql: {}", maxBatchSqlSize, cost, sql);
                }
                pendingCount = 0;
            } else {
                pendingCount++;
            }
        }
        if (pendingCount > 0) {
            long start = System.currentTimeMillis();
            preparedStatement.executeBatch();
            long end = System.currentTimeMillis();
            long cost = end - start;
            if (log.isDebugEnabled()) {
                log.debug("execute batch sql,batch size: {}, cost:{},sql: {}", pendingCount, cost, sql);
            }
        }
    }
}

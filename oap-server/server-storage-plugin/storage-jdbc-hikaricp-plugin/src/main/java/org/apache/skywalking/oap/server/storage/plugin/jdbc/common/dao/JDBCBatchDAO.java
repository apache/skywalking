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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.PartitionPolicy;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.BatchSQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

@Slf4j
public class JDBCBatchDAO implements IBatchDAO {
    private final JDBCClient jdbcClient;
    private final BatchQueue<PrepareRequest> queue;
    private final int maxBatchSqlSize;

    public JDBCBatchDAO(JDBCClient jdbcClient, int maxBatchSqlSize, int asyncBatchPersistentPoolSize) {
        this.jdbcClient = jdbcClient;
        if (log.isDebugEnabled()) {
            log.debug("JDBC_ASYNC_BATCH_PERSISTENT poolSize: {}, maxBatchSqlSize:{}", asyncBatchPersistentPoolSize, maxBatchSqlSize);
        }
        this.maxBatchSqlSize = maxBatchSqlSize;
        this.queue = BatchQueueManager.create(
            "JDBC_ASYNC_BATCH_PERSISTENT",
            BatchQueueConfig.<PrepareRequest>builder()
                .threads(ThreadPolicy.fixed(asyncBatchPersistentPoolSize))
                .partitions(PartitionPolicy.fixed(asyncBatchPersistentPoolSize))
                .bufferSize(10_000)
                .consumer(batch -> flush(batch))
                .maxIdleMs(20)
                .build()
        );
    }

    @Override
    public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isEmpty(prepareRequests)) {
            return CompletableFuture.completedFuture(null);
        }

        List<PrepareRequest> sqls = new ArrayList<>();
        prepareRequests.forEach(prepareRequest -> {
            sqls.add(prepareRequest);
            SQLExecutor sqlExecutor = (SQLExecutor) prepareRequest;
            if (!CollectionUtils.isEmpty(sqlExecutor.getAdditionalSQLs())) {
                sqls.addAll(sqlExecutor.getAdditionalSQLs());
            }
        });

        if (log.isDebugEnabled()) {
            log.debug("to execute sql statements execute, data size: {}, maxBatchSqlSize: {}", sqls.size(), maxBatchSqlSize);
        }

        final var batchRequestsOfSql = sqls.stream().collect(Collectors.groupingBy(Function.identity()));
        batchRequestsOfSql.forEach((sql, requests) -> {
            try {
                final var batchSQLExecutor = new BatchSQLExecutor(jdbcClient, requests);
                batchSQLExecutor.invoke(maxBatchSqlSize);
            } catch (Exception e) {
                // Just to avoid one execution failure makes the rest of batch failure.
                log.error(e.getMessage(), e);
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("execute sql statements done, data size: {}, maxBatchSqlSize: {}", prepareRequests.size(), maxBatchSqlSize);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        this.queue.produce(insertRequest);
    }
}

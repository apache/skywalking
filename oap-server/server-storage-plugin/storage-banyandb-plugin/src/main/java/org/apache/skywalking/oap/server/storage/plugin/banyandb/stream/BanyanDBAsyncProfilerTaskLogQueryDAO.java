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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.Element;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * {@link AsyncProfilerTaskLogRecord} is a stream
 */
public class BanyanDBAsyncProfilerTaskLogQueryDAO extends AbstractBanyanDBDAO implements IAsyncProfilerTaskLogQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            AsyncProfilerTaskLogRecord.OPERATION_TIME,
            AsyncProfilerTaskLogRecord.INSTANCE_ID,
            AsyncProfilerTaskLogRecord.TASK_ID,
            AsyncProfilerTaskLogRecord.OPERATION_TYPE
    );

    private final int queryMaxSize;

    public BanyanDBAsyncProfilerTaskLogQueryDAO(BanyanDBStorageClient client, int taskQueryMaxSize) {
        super(client);
        // query log size use async-profiler task query max size * per log count
        this.queryMaxSize = taskQueryMaxSize * 50;
    }

    @Override
    public List<AsyncProfilerTaskLog> getTaskLogList() throws IOException {
        StreamQueryResponse resp = query(false, AsyncProfilerTaskLogRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setLimit(BanyanDBAsyncProfilerTaskLogQueryDAO.this.queryMaxSize);
                    }
                });

        final LinkedList<AsyncProfilerTaskLog> tasks = new LinkedList<>();
        for (final Element element : resp.getElements()) {
            tasks.add(buildAsyncProfilerTaskLog(element));
        }
        return tasks;
    }

    private AsyncProfilerTaskLog buildAsyncProfilerTaskLog(Element data) {
        int operationTypeInt = ((Number) data.getTagValue(AsyncProfilerTaskLogRecord.OPERATION_TYPE)).intValue();
        AsyncProfilerTaskLogOperationType operationType = AsyncProfilerTaskLogOperationType.parse(operationTypeInt);
        return AsyncProfilerTaskLog.builder()
                .id(data.getTagValue(AsyncProfilerTaskLogRecord.TASK_ID))
                .instanceId(data.getTagValue(AsyncProfilerTaskLogRecord.INSTANCE_ID))
                .operationType(operationType)
                .operationTime(((Number) data.getTagValue(AsyncProfilerTaskLogRecord.OPERATION_TIME)).longValue())
                .build();
    }
}

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
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BanyanDBAsyncProfilerTaskQueryDAO extends AbstractBanyanDBDAO implements IAsyncProfilerTaskQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            AsyncProfilerTaskRecord.SERVICE_ID,
            AsyncProfilerTaskRecord.SERVICE_INSTANCE_IDS,
            AsyncProfilerTaskRecord.TASK_ID,
            AsyncProfilerTaskRecord.CREATE_TIME,
            AsyncProfilerTaskRecord.DURATION,
            AsyncProfilerTaskRecord.EVENT_TYPES
    );

    private final int queryMaxSize;

    public BanyanDBAsyncProfilerTaskQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<AsyncProfilerTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        long startTS = LOWER_BOUND_TIME;
        long endTS = UPPER_BOUND_TIME;
        if (startTimeBucket != null) {
            startTS = TimeBucket.getTimestamp(startTimeBucket);
        }
        if (endTimeBucket != null) {
            endTS = TimeBucket.getTimestamp(endTimeBucket);
        }
        StreamQueryResponse resp = query(AsyncProfilerTaskRecord.INDEX_NAME, TAGS, new TimestampRange(startTS, endTS),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(ProfileTaskRecord.SERVICE_ID, serviceId));
                        }

                        if (limit != null) {
                            query.setLimit(limit);
                        } else {
                            query.setLimit(BanyanDBAsyncProfilerTaskQueryDAO.this.queryMaxSize);
                        }
                        query.setOrderBy(new AbstractQuery.OrderBy(AbstractQuery.Sort.DESC));
                    }
                });

        List<AsyncProfilerTask> tasks = new ArrayList<>(resp.size());
        for (final RowEntity entity : resp.getElements()) {
            tasks.add(buildAsyncProfilerTask(entity));
        }
        return tasks;
    }

    @Override
    public AsyncProfilerTask getById(String id) throws IOException {
        StreamQueryResponse resp = query(AsyncProfilerTaskRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(id)) {
                            query.and(eq(AsyncProfilerTaskRecord.TASK_ID, id));
                        }
                        query.setLimit(1);
                    }
                });

        if (resp.size() == 0) {
            return null;
        }

        return buildAsyncProfilerTask(resp.getElements().get(0));
    }

    private AsyncProfilerTask buildAsyncProfilerTask(RowEntity data) {
        List<String> events = data.getTagValue(AsyncProfilerTaskRecord.EVENT_TYPES);

        return AsyncProfilerTask.builder()
                .id(data.getTagValue(AsyncProfilerTaskRecord.TASK_ID))
                .serviceId(data.getTagValue(AsyncProfilerTaskRecord.SERVICE_ID))
                .serviceInstanceIds(data.getTagValue(AsyncProfilerTaskRecord.SERVICE_INSTANCE_IDS))
                .createTime(((Number) data.getTagValue(AsyncProfilerTaskRecord.CREATE_TIME)).longValue())
                .duration(((Number) data.getTagValue(AsyncProfilerTaskRecord.DURATION)).intValue())
                .events(AsyncProfilerEventType.valueOfList(events))
                .execArgs(data.getTagValue(AsyncProfilerTaskRecord.EXEC_ARGS))
                .build();
    }
}

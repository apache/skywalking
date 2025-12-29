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
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

@Slf4j
public class BanyanDBPprofTaskQueryDAO extends AbstractBanyanDBDAO implements IPprofTaskQueryDAO {
    private static final Gson GSON = new Gson();
    private static final Set<String> TAGS = ImmutableSet.of(
            PprofTaskRecord.SERVICE_ID,
            PprofTaskRecord.SERVICE_INSTANCE_IDS,
            PprofTaskRecord.TASK_ID,
            PprofTaskRecord.CREATE_TIME,
            PprofTaskRecord.EVENT_TYPES,
            PprofTaskRecord.DURATION,
            PprofTaskRecord.DUMP_PERIOD
    );

    private final int queryMaxSize;

    public BanyanDBPprofTaskQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<PprofTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        long startTS = LOWER_BOUND_TIME;
        long endTS = UPPER_BOUND_TIME;
        if (startTimeBucket != null) {
            startTS = TimeBucket.getTimestamp(startTimeBucket);
        }
        if (endTimeBucket != null) {
            endTS = TimeBucket.getTimestamp(endTimeBucket);
        }
        StreamQueryResponse resp = query(false, PprofTaskRecord.INDEX_NAME, TAGS, new TimestampRange(startTS, endTS),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(PprofTaskRecord.SERVICE_ID, serviceId));
                        }

                        if (limit != null) {
                            query.setLimit(limit);
                        } else {
                            query.setLimit(BanyanDBPprofTaskQueryDAO.this.queryMaxSize);
                        }
                        query.setOrderBy(new AbstractQuery.OrderBy(AbstractQuery.Sort.DESC));
                    }
                });

        List<PprofTask> tasks = new ArrayList<>(resp.size());
        for (final RowEntity entity : resp.getElements()) {
            tasks.add(buildPprofTask(entity));
        }
        return tasks;
    }

    @Override
    public PprofTask getById(String id) throws IOException {
        StreamQueryResponse resp = query(false, PprofTaskRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(id)) {
                            query.and(eq(PprofTaskRecord.TASK_ID, id));
                        }
                        query.setLimit(1);
                    }
                });

        if (resp.size() == 0) {
            return null;
        }

        return buildPprofTask(resp.getElements().get(0));
    }

    private PprofTask buildPprofTask(RowEntity data) {
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        
        String serviceInstanceIds = data.getTagValue(PprofTaskRecord.SERVICE_INSTANCE_IDS);
        List<String> serviceInstanceIdList = GSON.fromJson(serviceInstanceIds, listType);
        
        // Convert string events to PprofEventType enum
        String eventsStr = data.getTagValue(PprofTaskRecord.EVENT_TYPES);
        PprofEventType eventType = null;
        if (StringUtil.isNotEmpty(eventsStr)) {
            try {
                eventType = PprofEventType.valueOfString(eventsStr);
            } catch (Exception e) {
                // Default to CPU if conversion fails
                eventType = PprofEventType.CPU;
                log.warn("Failed to parse pprof event type: {}, using CPU as default", eventsStr, e);
            }
        }
        
        return PprofTask.builder()
                .id(data.getTagValue(PprofTaskRecord.TASK_ID))
                .serviceId(data.getTagValue(PprofTaskRecord.SERVICE_ID))
                .serviceInstanceIds(serviceInstanceIdList)
                .createTime(((Number) data.getTagValue(PprofTaskRecord.CREATE_TIME)).longValue())
                .events(eventType)
                .duration(((Number) data.getTagValue(PprofTaskRecord.DURATION)).intValue())
                .dumpPeriod(((Number) data.getTagValue(PprofTaskRecord.DUMP_PERIOD)).intValue())
                .build();
    }
}

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
import com.google.gson.Gson;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BanyanDBEBPFProfilingTaskDAO extends AbstractBanyanDBDAO implements IEBPFProfilingTaskDAO {
    private static final Gson GSON = new Gson();

    public BanyanDBEBPFProfilingTaskDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<EBPFProfilingTask> queryTasks(List<String> serviceIdList, EBPFProfilingTargetType targetType, long taskStartTime, long latestUpdateTime) throws IOException {
        List<EBPFProfilingTask> tasks = new ArrayList<>();
        for (final String serviceId : serviceIdList) {
            StreamQueryResponse resp = query(EBPFProfilingTaskRecord.INDEX_NAME,
                    ImmutableSet.of(EBPFProfilingTaskRecord.SERVICE_ID,
                            EBPFProfilingTaskRecord.PROCESS_LABELS_JSON,
                            EBPFProfilingTaskRecord.TRIGGER_TYPE,
                            EBPFProfilingTaskRecord.START_TIME,
                            EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION,
                            EBPFProfilingTaskRecord.TARGET_TYPE,
                            EBPFProfilingTaskRecord.CREATE_TIME,
                            EBPFProfilingTaskRecord.LAST_UPDATE_TIME,
                            EBPFProfilingTaskRecord.TIME_BUCKET),
                    new QueryBuilder<StreamQuery>() {
                        @Override
                        protected void apply(StreamQuery query) {
                            query.and(eq(EBPFProfilingTaskRecord.SERVICE_ID, serviceId));
                            if (targetType != null) {
                                query.and(eq(EBPFProfilingTaskRecord.TARGET_TYPE, targetType.value()));
                            }
                            if (taskStartTime > 0) {
                                query.and(gte(EBPFProfilingTaskRecord.START_TIME, taskStartTime));
                            }
                            if (latestUpdateTime > 0) {
                                query.and(gte(EBPFProfilingTaskRecord.LAST_UPDATE_TIME, latestUpdateTime));
                            }
                            query.setOrderBy(new AbstractQuery.OrderBy(EBPFProfilingTaskRecord.CREATE_TIME, AbstractQuery.Sort.DESC));
                        }
                    });
            tasks.addAll(resp.getElements().stream().map(this::buildTask).collect(Collectors.toList()));
        }

        return tasks;
    }

    private EBPFProfilingTask buildTask(final RowEntity rowEntity) {
        final EBPFProfilingTaskRecord.Builder builder = new EBPFProfilingTaskRecord.Builder();
        final EBPFProfilingTaskRecord record = builder.storage2Entity(new BanyanDBConverter.StorageToStream(
                EBPFProfilingTaskRecord.INDEX_NAME,
                rowEntity));

        final EBPFProfilingTask task = new EBPFProfilingTask();
        task.setTaskId(record.id());
        task.setServiceId(record.getServiceId());
        task.setServiceName(IDManager.ServiceID.analysisId(record.getServiceId()).getName());
        if (StringUtil.isNotEmpty(record.getProcessLabelsJson())) {
            task.setProcessLabels(GSON.<List<String>>fromJson(record.getProcessLabelsJson(), ArrayList.class));
        } else {
            task.setProcessLabels(Collections.emptyList());
        }
        task.setTaskStartTime(record.getStartTime());
        task.setTriggerType(EBPFProfilingTriggerType.valueOf(record.getTriggerType()));
        task.setFixedTriggerDuration(record.getFixedTriggerDuration());
        task.setTargetType(EBPFProfilingTargetType.valueOf(record.getTargetType()));
        task.setCreateTime(record.getCreateTime());
        task.setLastUpdateTime(record.getLastUpdateTime());
        return task;
    }
}

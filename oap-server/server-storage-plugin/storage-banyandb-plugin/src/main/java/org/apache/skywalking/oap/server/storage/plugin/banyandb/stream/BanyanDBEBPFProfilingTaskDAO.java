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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBEBPFProfilingTaskDAO extends AbstractBanyanDBDAO implements IEBPFProfilingTaskDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            EBPFProfilingTaskRecord.LOGICAL_ID,
            EBPFProfilingTaskRecord.SERVICE_ID,
            EBPFProfilingTaskRecord.INSTANCE_ID,
            EBPFProfilingTaskRecord.PROCESS_LABELS_JSON,
            EBPFProfilingTaskRecord.TRIGGER_TYPE,
            EBPFProfilingTaskRecord.START_TIME,
            EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION,
            EBPFProfilingTaskRecord.TARGET_TYPE,
            EBPFProfilingTaskRecord.CREATE_TIME,
            EBPFProfilingTaskRecord.LAST_UPDATE_TIME,
            EBPFProfilingTaskRecord.EXTENSION_CONFIG_JSON);

    public BanyanDBEBPFProfilingTaskDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByServices(List<String> serviceIdList, EBPFProfilingTriggerType triggerType,
                                                        long taskStartTime, long latestUpdateTime) throws IOException {
        List<EBPFProfilingTaskRecord> tasks = new ArrayList<>();
        for (final String serviceId : serviceIdList) {
            StreamQueryResponse resp = query(EBPFProfilingTaskRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        query.and(eq(EBPFProfilingTaskRecord.SERVICE_ID, serviceId));
                        appendTimeQuery(this, query, taskStartTime, latestUpdateTime);
                        if (triggerType != null) {
                            query.and(eq(EBPFProfilingTaskRecord.TRIGGER_TYPE, triggerType.value()));
                        }
                        query.setOrderBy(new AbstractQuery.OrderBy(EBPFProfilingTaskRecord.CREATE_TIME, AbstractQuery.Sort.DESC));
                    }
                });
            tasks.addAll(resp.getElements().stream().map(this::buildTask).collect(Collectors.toList()));
        }

        return tasks;
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByTargets(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targetTypes,
                                                       EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
        List<EBPFProfilingTaskRecord> tasks = new ArrayList<>();
        for (final EBPFProfilingTargetType targetType : targetTypes) {
            StreamQueryResponse resp = query(EBPFProfilingTaskRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(EBPFProfilingTaskRecord.SERVICE_ID, serviceId));
                        }
                        if (StringUtil.isNotEmpty(serviceInstanceId)) {
                            query.and(eq(EBPFProfilingTaskRecord.INSTANCE_ID, serviceInstanceId));
                        }
                        if (CollectionUtils.isNotEmpty(targetTypes)) {
                            query.and(eq(EBPFProfilingTaskRecord.TRIGGER_TYPE, triggerType.value()));
                        }
                        query.and(eq(EBPFProfilingTaskRecord.TARGET_TYPE, targetType.value()));
                        appendTimeQuery(this, query, taskStartTime, latestUpdateTime);
                        query.setOrderBy(new AbstractQuery.OrderBy(EBPFProfilingTaskRecord.CREATE_TIME, AbstractQuery.Sort.DESC));
                    }
                });
            tasks.addAll(resp.getElements().stream().map(this::buildTask).collect(Collectors.toList()));
        }

        return tasks;
    }

    @Override
    public List<EBPFProfilingTaskRecord> getTaskRecord(String id) throws IOException {
        StreamQueryResponse resp = query(EBPFProfilingTaskRecord.INDEX_NAME, TAGS,
            new QueryBuilder<StreamQuery>() {
                @Override
                protected void apply(StreamQuery query) {
                    query.and(eq(EBPFProfilingTaskRecord.LOGICAL_ID, id));
                }
            });
        return resp.getElements().stream().map(this::buildTask).collect(Collectors.toList());
    }

    private void appendTimeQuery(QueryBuilder<StreamQuery> builder, StreamQuery query, long taskStartTime, long latestUpdateTime) {
        if (taskStartTime > 0) {
            query.and(builder.gte(EBPFProfilingTaskRecord.START_TIME, taskStartTime));
        }
        if (latestUpdateTime > 0) {
            query.and(builder.gt(EBPFProfilingTaskRecord.LAST_UPDATE_TIME, latestUpdateTime));
        }
    }

    private EBPFProfilingTaskRecord buildTask(final RowEntity rowEntity) {
        final EBPFProfilingTaskRecord.Builder builder = new EBPFProfilingTaskRecord.Builder();
        return builder.storage2Entity(new BanyanDBConverter.StorageToStream(
                EBPFProfilingTaskRecord.INDEX_NAME,
                rowEntity));
    }
}

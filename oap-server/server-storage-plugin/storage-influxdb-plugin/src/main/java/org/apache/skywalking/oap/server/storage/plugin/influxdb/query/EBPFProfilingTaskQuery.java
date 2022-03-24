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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingProcessFinderType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.regex;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class EBPFProfilingTaskQuery implements IEBPFProfilingTaskDAO {
    private final InfluxClient client;

    @Override
    public List<EBPFProfilingTask> queryTasks(EBPFProfilingProcessFinder finder, EBPFProfilingTargetType targetType, long taskStartTime, long latestUpdateTime) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select(
                InfluxConstants.ID_COLUMN,
                EBPFProfilingTaskRecord.PROCESS_FIND_TYPE,
                EBPFProfilingTaskRecord.SERVICE_ID,
                EBPFProfilingTaskRecord.INSTANCE_ID,
                EBPFProfilingTaskRecord.PROCESS_ID,
                EBPFProfilingTaskRecord.PROCESS_NAME,
                EBPFProfilingTaskRecord.START_TIME,
                EBPFProfilingTaskRecord.TRIGGER_TYPE,
                EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION,
                EBPFProfilingTaskRecord.TARGET_TYPE,
                EBPFProfilingTaskRecord.CREATE_TIME,
                EBPFProfilingTaskRecord.LAST_UPDATE_TIME
        )
                .from(client.getDatabase(), EBPFProfilingTaskRecord.INDEX_NAME)
                .where();

        if (finder.getFinderType() != null) {
            query.and(eq(EBPFProfilingTaskRecord.PROCESS_FIND_TYPE, finder.getFinderType().value()));
        }
        if (StringUtil.isNotEmpty(finder.getServiceId())) {
            query.and(eq(EBPFProfilingTaskRecord.SERVICE_ID, finder.getServiceId()));
        }
        if (StringUtil.isNotEmpty(finder.getInstanceId())) {
            query.and(eq(EBPFProfilingTaskRecord.INSTANCE_ID, finder.getInstanceId()));
        }
        if (CollectionUtils.isNotEmpty(finder.getProcessIdList())) {
            query.and(regex(EBPFProfilingTaskRecord.PROCESS_ID, Joiner.on("|").join(finder.getProcessIdList())));
        }
        if (targetType != null) {
            query.and(eq(EBPFProfilingTaskRecord.TARGET_TYPE, targetType.value()));
        }
        if (taskStartTime > 0) {
            query.and(gte(EBPFProfilingTaskRecord.START_TIME, taskStartTime));
        }
        if (latestUpdateTime > 0) {
            query.and(eq(EBPFProfilingTaskRecord.LAST_UPDATE_TIME, latestUpdateTime));
        }

        return buildTasks(query);
    }

    private List<EBPFProfilingTask> buildTasks(WhereQueryImpl<SelectQueryImpl> query) throws IOException {
        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {}, result: {}", query.getCommand(), series);
        }

        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        final ArrayList<EBPFProfilingTask> tasks = new ArrayList<>();
        for (List<Object> values : series.getValues()) {
            final EBPFProfilingTask task = new EBPFProfilingTask();
            task.setTaskId((String) values.get(1));
            task.setProcessFinderType(EBPFProfilingProcessFinderType.valueOf((int) values.get(2)));
            final String serviceId = (String) values.get(3);
            task.setServiceId(serviceId);
            task.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String instanceId = (String) values.get(4);
            task.setInstanceId(instanceId);
            task.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            task.setProcessId((String) values.get(5));
            task.setProcessName((String) values.get(6));
            task.setTaskStartTime((long) values.get(7));
            task.setTriggerType(EBPFProfilingTriggerType.valueOf((int) values.get(8)));
            task.setFixedTriggerDuration((long) values.get(9));
            task.setTargetType(EBPFProfilingTargetType.valueOf((int) values.get(10)));
            task.setCreateTime((long) values.get(11));
            task.setLastUpdateTime((long) values.get(12));
            tasks.add(task);
        }

        return tasks;
    }
}

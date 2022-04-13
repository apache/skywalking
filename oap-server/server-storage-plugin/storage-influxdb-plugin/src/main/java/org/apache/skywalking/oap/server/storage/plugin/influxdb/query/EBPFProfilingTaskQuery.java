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
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
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
    private static final Gson GSON = new Gson();
    private final InfluxClient client;

    @Override
    public List<EBPFProfilingTask> queryTasks(List<String> serviceIdList, EBPFProfilingTargetType targetType, long taskStartTime, long latestUpdateTime) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select(
                InfluxConstants.ID_COLUMN,
                EBPFProfilingTaskRecord.SERVICE_ID,
                EBPFProfilingTaskRecord.PROCESS_LABELS_JSON,
                EBPFProfilingTaskRecord.START_TIME,
                EBPFProfilingTaskRecord.TRIGGER_TYPE,
                EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION,
                EBPFProfilingTaskRecord.TARGET_TYPE,
                EBPFProfilingTaskRecord.CREATE_TIME,
                EBPFProfilingTaskRecord.LAST_UPDATE_TIME
        )
                .from(client.getDatabase(), EBPFProfilingTaskRecord.INDEX_NAME)
                .where();

        query.and(regex(EBPFProfilingTaskRecord.SERVICE_ID, "/" + Joiner.on("|").join(serviceIdList) + "/"));
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
            final String serviceId = (String) values.get(2);
            task.setServiceId(serviceId);
            task.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String processLabelString = (String) values.get(3);
            if (StringUtil.isNotEmpty(processLabelString)) {
                task.setProcessLabels(GSON.<List<String>>fromJson(processLabelString, ArrayList.class));
            } else {
                task.setProcessLabels(Collections.emptyList());
            }
            task.setTaskStartTime(((Number) values.get(4)).longValue());
            task.setTriggerType(EBPFProfilingTriggerType.valueOf(((Number) values.get(5)).intValue()));
            task.setFixedTriggerDuration(((Number) values.get(6)).longValue());
            task.setTargetType(EBPFProfilingTargetType.valueOf(((Number) values.get(7)).intValue()));
            task.setCreateTime(((Number) values.get(8)).longValue());
            task.setLastUpdateTime(((Number) values.get(9)).longValue());
            tasks.add(task);
        }

        return tasks;
    }
}

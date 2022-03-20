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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
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
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class EBPFProfilingScheduleQuery implements IEBPFProfilingScheduleDAO {
    private final InfluxClient client;

    @Override
    public List<EBPFProfilingSchedule> querySchedules(String taskId, long startTimeBucket, long endTimeBucket) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select(
                InfluxConstants.ID_COLUMN,
                EBPFProfilingScheduleRecord.TASK_ID,
                EBPFProfilingScheduleRecord.PROCESS_ID,
                EBPFProfilingScheduleRecord.START_TIME,
                EBPFProfilingScheduleRecord.END_TIME
        )
                .from(client.getDatabase(), EBPFProfilingScheduleRecord.INDEX_NAME)
                .where();

        query.and(eq(EBPFProfilingScheduleRecord.TASK_ID, taskId));
        query.and(gte(EBPFProfilingScheduleRecord.START_TIME, TimeBucket.getTimestamp(startTimeBucket)));
        query.and(lte(EBPFProfilingScheduleRecord.START_TIME, TimeBucket.getTimestamp(endTimeBucket)));

        return buildSchedules(query);
    }

    private List<EBPFProfilingSchedule> buildSchedules(WhereQueryImpl<SelectQueryImpl> query) throws IOException {
        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {}, result: {}", query.getCommand(), series);
        }

        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        final ArrayList<EBPFProfilingSchedule> schedules = new ArrayList<>();
        for (List<Object> values : series.getValues()) {
            final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
            schedule.setScheduleId((String) values.get(1));
            schedule.setTaskId((String) values.get(2));
            schedule.setProcessId((String) values.get(3));
            schedule.setStartTime((long) values.get(4));
            schedule.setEndTime((long) values.get(5));
            schedules.add(schedule);
        }
        return schedules;
    }
}

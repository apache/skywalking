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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class ProfileTaskLogQuery implements IProfileTaskLogQueryDAO {
    private InfluxClient client;
    private int fetchTaskLogMaxSize;

    public ProfileTaskLogQuery(InfluxClient client, int fetchTaskLogMaxSize) {
        this.client = client;
        this.fetchTaskLogMaxSize = fetchTaskLogMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .function("top", ProfileTaskLogRecord.OPERATION_TIME, fetchTaskLogMaxSize)
            .column("id")
            .column(ProfileTaskLogRecord.TASK_ID)
            .column(ProfileTaskLogRecord.INSTANCE_ID)
            .column(ProfileTaskLogRecord.OPERATION_TIME)
            .column(ProfileTaskLogRecord.OPERATION_TYPE)
            .from(client.getDatabase(), ProfileTaskLogRecord.INDEX_NAME)
            .where();

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }
        List<String> columns = series.getColumns();
        Map<String, Integer> columnsMap = Maps.newHashMap();
        for (int i = 0; i < columns.size(); i++) {
            columnsMap.put(columns.get(i), i);
        }

        List<ProfileTaskLog> taskLogs = Lists.newArrayList();
        series.getValues().stream()
              // re-sort by self, because of the result order by time.
              .sorted((a, b) -> Long.compare(((Number) b.get(1)).longValue(), ((Number) a.get(1)).longValue()))
              .forEach(values -> {
                  taskLogs.add(ProfileTaskLog.builder()
                                             .id((String) values.get(columnsMap.get("id")))
                                             .taskId((String) values.get(columnsMap.get(ProfileTaskLogRecord.TASK_ID)))
                                             .instanceId(
                                                 (int) values.get(columnsMap.get(ProfileTaskLogRecord.INSTANCE_ID)))
                                             .operationTime(
                                                 (Long) values.get(columnsMap.get(ProfileTaskLogRecord.OPERATION_TIME)))
                                             .operationType(ProfileTaskLogOperationType.parse(
                                                 (int) values.get(columnsMap.get(ProfileTaskLogRecord.OPERATION_TYPE))))
                                             .build());
              });
        return taskLogs;
    }
}

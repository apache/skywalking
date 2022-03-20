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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lt;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class EBPFProfilingDataQuery implements IEBPFProfilingDataDAO {
    private final InfluxClient client;

    @Override
    public List<EBPFProfilingDataRecord> queryData(String taskId, long beginTime, long endTime) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select(
                EBPFProfilingDataRecord.SCHEDULE_ID,
                EBPFProfilingDataRecord.TASK_ID,
                EBPFProfilingDataRecord.STACK_ID_LIST,
                EBPFProfilingDataRecord.STACKS_BINARY,
                EBPFProfilingDataRecord.STACK_DUMP_COUNT,
                EBPFProfilingDataRecord.UPLOAD_TIME
        )
                .from(client.getDatabase(), EBPFProfilingDataRecord.INDEX_NAME)
                .where();

        query.and(eq(EBPFProfilingDataRecord.TASK_ID, taskId));
        query.and(gte(EBPFProfilingDataRecord.UPLOAD_TIME, beginTime));
        query.and(lt(EBPFProfilingDataRecord.UPLOAD_TIME, endTime));

        return buildDataList(query);
    }

    private List<EBPFProfilingDataRecord> buildDataList(WhereQueryImpl<SelectQueryImpl> query) throws IOException {
        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {}, result: {}", query.getCommand(), series);
        }

        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        final ArrayList<EBPFProfilingDataRecord> dataList = new ArrayList<>();
        for (List<Object> values : series.getValues()) {
            final EBPFProfilingDataRecord data = new EBPFProfilingDataRecord();
            data.setScheduleId((String) values.get(1));
            data.setTaskId((String) values.get(2));
            data.setStackIdList((String) values.get(3));
            final String stackStr = (String) values.get(4);
            if (StringUtil.isNotEmpty(stackStr)) {
                data.setStacksBinary(Base64.getDecoder().decode(stackStr));
            } else {
                data.setStacksBinary(new byte[0]);
            }
            data.setStackDumpCount((Long) values.get(5));
            data.setUploadTime((Long) values.get(6));

            dataList.add(data);
        }
        return dataList;
    }
}

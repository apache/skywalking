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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleTraffic;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class IoTDBEBPFProfilingScheduleDAO implements IEBPFProfilingScheduleDAO {
    private final IoTDBClient client;
    private final StorageBuilder<EBPFProfilingScheduleTraffic> storageBuilder = new EBPFProfilingScheduleTraffic.Builder();

    @Override
    public List<EBPFProfilingSchedule> querySchedules(String taskId, long startTimeBucket, long endTimeBucket) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, EBPFProfilingScheduleTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(taskId)) {
            indexAndValueMap.put(IoTDBIndexes.TASK_ID_INX, taskId);
        }
        query = client.addQueryIndexValue(EBPFProfilingScheduleTraffic.INDEX_NAME, query, indexAndValueMap);

        StringBuilder where = new StringBuilder(" where ");
        if (startTimeBucket > 0) {
            where.append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTimeBucket)).append(" and ");
        }
        if (endTimeBucket > 0) {
            where.append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(startTimeBucket)).append(" and ");
        }
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(EBPFProfilingScheduleTraffic.INDEX_NAME, query.toString(), storageBuilder);
        List<EBPFProfilingSchedule> scheduleList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> scheduleList.add(parseSchedule((EBPFProfilingScheduleTraffic) storageData)));
        return scheduleList;
    }

    public EBPFProfilingSchedule parseSchedule(EBPFProfilingScheduleTraffic traffic) {
        final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
        schedule.setTaskId(traffic.getTaskId());
        schedule.setProcessId(traffic.getProcessId());
        schedule.setStartTime(traffic.getStartTime());
        schedule.setEndTime(traffic.getEndTime());
        return schedule;
    }
}

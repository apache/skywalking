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

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.utils.IoTDBUtils;

@Slf4j
@RequiredArgsConstructor
public class IoTDBEBPFProfilingTaskDAO implements IEBPFProfilingTaskDAO {
    private static final Gson GSON = new Gson();

    private final IoTDBClient client;
    private final StorageBuilder<EBPFProfilingTaskRecord> storageBuilder = new EBPFProfilingTaskRecord.Builder();

    @Override
    public List<EBPFProfilingTask> queryTasks(List<String> serviceIdList,
                                              EBPFProfilingTargetType targetType,
                                              long taskStartTime, long latestUpdateTime)
            throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, EBPFProfilingTaskRecord.INDEX_NAME);
        for (int i = 0; i < serviceIdList.size(); i++) {
            if (i > 0) {
                query.append(", ");
            }
            Map<String, String> indexAndValueMap = new HashMap<>();
            indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceIdList.get(i));
            IoTDBUtils.addQueryIndexValue(EBPFProfilingTaskRecord.INDEX_NAME, query, indexAndValueMap);
        }

        StringBuilder where = new StringBuilder(" where ");
        if (taskStartTime > 0) {
            where.append(EBPFProfilingTaskRecord.START_TIME)
                 .append(" >= ").append(taskStartTime)
                 .append(" and ");
        }
        if (latestUpdateTime > 0) {
            where.append(EBPFProfilingTaskRecord.LAST_UPDATE_TIME)
                 .append(" > ").append(latestUpdateTime)
                 .append(" and ");
        }
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(EBPFProfilingTaskRecord.INDEX_NAME,
                                                                       query.toString(), storageBuilder);
        List<EBPFProfilingTask> taskList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> taskList.add(parseTask((EBPFProfilingTaskRecord) storageData)));
        return taskList;
    }

    private EBPFProfilingTask parseTask(EBPFProfilingTaskRecord record) {
        final EBPFProfilingTask task = new EBPFProfilingTask();
        task.setTaskId(record.id());
        task.setServiceId(record.getServiceId());
        task.setServiceName(IDManager.ServiceID.analysisId(record.getServiceId()).getName());
        final String processLabelsJson = record.getProcessLabelsJson();
        if (StringUtil.isNotEmpty(processLabelsJson)) {
            task.setProcessLabels(GSON.<List<String>>fromJson(processLabelsJson, ArrayList.class));
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